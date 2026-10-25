"""Socket-bound admission and serialized, bounded input dispatch.

The server authenticates hello/session/CSRF, bounds sockets to eight, rejects
JSON duplicate keys and enforces frame/rate limits before calling handle.
All methods run on the owning asyncio loop; native callers marshal onto it.

Injected dispatch(kind, fields) is async and cancellation-safe: it owns and
cleans up any partial connect resources, never returns an owned resource, and
bounds connect duration itself. fence() synchronously prevents ordinary sends;
dispatch('stop', {}) performs cleanup only. No real adapter is installed here.
"""

import asyncio
from collections import deque
import secrets
import time


_KEYS = frozenset(('UP', 'DOWN', 'LEFT', 'RIGHT', 'OK', 'HOME', 'BACK',
                   'VOLUME_UP', 'VOLUME_DOWN', 'PLAY_PAUSE'))
_FIELDS = {
    'take_control': set(), 'stop': set(),
    **{kind: {'lease'} for kind in ('release_control', 'connect', 'netflix',
                                   'press', 'release', 'tap', 'heartbeat')},
    'key': {'lease', 'key'},
    'move': {'lease', 'dx', 'dy'}, 'scroll': {'lease', 'dx', 'dy'},
}


class Controller:
    def __init__(self, dispatch=None, fence=None, clock=time.monotonic):
        self._dispatch = dispatch
        self._fence = fence
        self._clock = clock
        self._seq = {}
        self._owner = None
        self._generation = 0
        self._heartbeat = 0.0
        self._connected = False
        self._connecting = False
        self._drag_start = None
        self._drag_idle = None
        self._queue = deque()
        self._motion = None
        self._last_motion = float('-inf')
        self._cleanup = False
        self._closed = False
        self._broken = False
        self._wake = asyncio.Event()
        self._worker = None
        self._operation = None

    @property
    def status(self):
        """Fresh safe snapshot; never exposes lease, owner, input or errors."""
        return {'available': self._dispatch is not None and not self._broken and not self._closed,
                'connected': self._connected, 'controlled': self._owner is not None,
                'dragging': self._drag_start is not None}

    def status_for(self, session_id: str, socket_id: str) -> dict:
        """Read-only socket snapshot; only the exact owner receives its lease."""
        status = self.status
        lease = self._owner[2] if self._owner is not None and self._owner[:2] == (session_id, socket_id) else None
        return {'available': status['available'], 'connected': status['connected'], 'lease': lease}

    async def handle(self, session_id: str, socket_id: str, message: dict) -> dict:
        self.tick()
        seq = message.get('seq') if isinstance(message, dict) else None
        ack = {'kind': 'ack', 'seq': seq if type(seq) is int and seq > 0 else None}

        def fail(error):
            return {**ack, 'status': 'failed', 'error': error}

        if self._closed:
            return fail('closed')
        if not isinstance(message, dict):
            return fail('schema')
        if type(message.get('version')) is not int or message['version'] != 1:
            self.stop('version')
            return fail('version')
        if type(seq) is not int or seq <= 0 or seq != self._seq.get(socket_id, 0) + 1:
            return fail('sequence')
        self._seq[socket_id] = seq
        kind = message.get('kind')
        if not isinstance(kind, str) or kind not in _FIELDS:
            return fail('schema')
        if set(message) != {'version', 'seq', 'kind'} | _FIELDS[kind]:
            return fail('schema')
        if 'lease' in message and (not isinstance(message['lease'], str) or not message['lease']):
            return fail('schema')
        if kind == 'key' and (not isinstance(message['key'], str) or message['key'] not in _KEYS):
            return fail('schema')
        if kind in ('move', 'scroll'):
            bound = 256 if kind == 'move' else 1
            if any(type(message[axis]) is not int or abs(message[axis]) > bound for axis in ('dx', 'dy')):
                return fail('schema')
        if kind == 'stop':
            self.stop('remote')
        elif kind == 'take_control':
            if self._owner is not None:
                return fail('busy')
            if self._broken:
                return fail('unavailable')
            lease = secrets.token_urlsafe(32)
            self._generation += 1
            self._owner = (session_id, socket_id, lease)
            self._heartbeat = self._clock()
            return {**ack, 'status': 'admitted', 'lease': lease}
        else:
            if self._owner != (session_id, socket_id, message['lease']):
                return fail('not_owner')
            if kind == 'heartbeat':
                self._heartbeat = self._clock()
            elif kind == 'release_control':
                self.stop('released')
            else:
                if self._dispatch is None or self._broken:
                    return fail('unavailable')
                if kind == 'connect':
                    if self._connected or self._connecting:
                        return fail('state')
                    self._connecting = True
                elif not self._connected:
                    return fail('disconnected')
                if kind == 'press':
                    if self._drag_start is not None:
                        return fail('state')
                    self._drag_start = self._drag_idle = self._clock()
                elif kind in ('tap', 'scroll') and self._drag_start is not None:
                    return fail('state')
                fields = {key: message[key] for key in _FIELDS[kind] if key != 'lease'}
                item = (self._generation, kind, fields)
                if kind == 'move':
                    if self._motion is not None:
                        previous = self._motion[2]
                        fields = {axis: max(-256, min(256, previous[axis] + fields[axis]))
                                  for axis in ('dx', 'dy')}
                    self._motion = (self._generation, kind, fields)
                    if self._drag_start is not None:
                        self._drag_idle = self._clock()
                elif kind == 'release':
                    # Pending presses/motion must not reassert a button after release.
                    self._queue.clear()
                    self._motion = None
                    self._drag_start = self._drag_idle = None
                    self._queue.appendleft(item)
                else:
                    if len(self._queue) + (self._motion is not None) >= 8:
                        self.stop('overflow')
                        return fail('overflow')
                    # Freeze prior motion at the transition; later deltas cannot merge across it.
                    if self._motion is not None:
                        self._queue.append(self._motion)
                        self._motion = None
                    self._queue.append(item)
                self._start_worker()
        return {**ack, 'status': 'admitted'}

    def stop(self, reason: str) -> None:
        """Fence synchronously, invalidate work, then request serialized cleanup."""
        self._generation += 1
        self._owner = None
        self._connected = self._connecting = False
        self._drag_start = self._drag_idle = None
        self._queue.clear()
        self._motion = None
        self._last_motion = float('-inf')
        if self._fence is not None:
            try:
                self._fence()
            except Exception:
                self._broken = True
        if self._operation is not None and not self._cleanup:
            self._operation.cancel()
        if self._dispatch is not None:
            self._cleanup = True
            self._start_worker()

    def socket_closed(self, socket_id: str) -> None:
        self._seq.pop(socket_id, None)
        if self._owner is not None and self._owner[1] == socket_id:
            self.stop('socket_closed')

    def revoke(self, session_id: str) -> None:
        if self._owner is not None and self._owner[0] == session_id:
            self.stop('revoked')

    def tick(self) -> None:
        now = self._clock()
        if self._owner is not None and now - self._heartbeat >= 3:
            self.stop('heartbeat_expired')
        elif self._drag_start is not None and (
                now - self._drag_start >= 15 or now - self._drag_idle >= 2):
            self.stop('drag_expired')
        self._wake.set()

    def _start_worker(self):
        if self._worker is None or self._worker.done():
            self._worker = asyncio.create_task(self._run())
        self._wake.set()

    async def _invoke(self, generation, kind, fields):
        def failed(reason):
            if kind == 'stop':
                # No new device ownership is safe while old cleanup is uncertain.
                self._broken = True
            if kind == 'stop' or generation == self._generation:
                self.stop(reason)

        operation = self._operation = asyncio.create_task(self._dispatch(kind, fields))
        try:
            # Connect is bounded by the adapter; ordinary writes/cleanup get 200ms.
            done, _ = await asyncio.wait({operation}, timeout=None if kind == 'connect' else .2)
            if not done:
                failed('dispatch_timeout')
                operation.cancel()
            try:
                await operation
            except asyncio.CancelledError:
                failed('dispatch_cancelled')
                return False
            self.tick()
            return bool(done) and generation == self._generation
        except Exception:
            failed('dispatch_failed')
            return False
        finally:
            self._operation = None

    async def _run(self):
        while True:
            self._wake.clear()
            if self._cleanup:
                generation = self._generation
                await self._invoke(generation, 'stop', {})
                # Cleanup failure forbids reuse; do not create a cleanup retry loop.
                self._cleanup = False
                if self._broken:
                    self._queue.clear()
                continue
            if self._closed:
                return
            self.tick()
            if self._cleanup:
                continue
            motion_ready = self._clock() - self._last_motion >= 1 / 30
            if self._queue and (self._queue[0][1] != 'move' or motion_ready):
                generation, kind, fields = self._queue.popleft()
            elif not self._queue and self._motion is not None and motion_ready:
                generation, kind, fields = self._motion
                self._motion = None
            else:
                self._wake.clear()
                # Queued motion and the coalesced slot share one 30 Hz deadline.
                delay = None if not self._queue and self._motion is None else max(0, 1 / 30 - (self._clock() - self._last_motion))
                try:
                    await asyncio.wait_for(self._wake.wait(), delay)
                except TimeoutError:
                    pass
                continue
            if generation != self._generation:
                continue
            if kind == 'move':
                self._last_motion = self._clock()
            if await self._invoke(generation, kind, fields) and kind == 'connect':
                self._connected = True
                self._connecting = False

    async def close(self):
        if not self._closed:
            self._closed = True
            self.stop('shutdown')
        if self._worker is not None:
            await self._worker
