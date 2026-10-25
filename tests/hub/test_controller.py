import asyncio
import unittest
from collections import defaultdict

from hub.controller import Controller


class ControllerTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.now = 100.0
        self.sent = []
        self.fences = []
        self.seq = defaultdict(int)
        self.behavior = self.record
        self.controller = Controller(self.dispatch, lambda: self.fences.append(len(self.sent)),
                                     clock=lambda: self.now)
        self.addAsyncCleanup(self.controller.close)

    async def dispatch(self, kind, fields):
        await self.behavior(kind, fields)

    async def record(self, kind, fields):
        self.sent.append((kind, fields))

    async def send(self, kind, *, session='owner', socket='one', **fields):
        self.seq[socket] += 1
        return await self.controller.handle(session, socket, dict(
            version=1, seq=self.seq[socket], kind=kind, **fields))

    async def settle(self):
        # Yield only; dispatch and cancellation are in-memory operations.
        for _ in range(20):
            await asyncio.sleep(0)

    async def own(self):
        result = await self.send('take_control')
        self.assertEqual(result['status'], 'admitted')
        self.assertEqual(result.get('kind'), 'ack')
        return result['lease']

    async def connected(self):
        lease = await self.own()
        self.assertEqual((await self.send('connect', lease=lease))['status'], 'admitted')
        await self.settle()
        self.assertTrue(self.controller.status['connected'])
        return lease

    async def test_no_backend_never_claims_device_success(self):
        await self.controller.close()
        self.controller = Controller(clock=lambda: self.now)
        self.addAsyncCleanup(self.controller.close)
        lease = await self.own()
        for kind, fields in [('connect', {}), ('key', {'key': 'HOME'}),
                             ('move', {'dx': 1, 'dy': 0}), ('press', {})]:
            result = await self.send(kind, lease=lease, **fields)
            self.assertEqual(result['error'], 'unavailable')
        self.assertEqual(self.controller.status, {
            'available': False, 'connected': False, 'controlled': True, 'dragging': False})

    async def test_status_for_exposes_lease_only_to_exact_session_and_socket(self):
        lease = await self.connected()
        expected = {'available': True, 'connected': True, 'lease': lease}
        snapshot = self.controller.status_for('owner', 'one')
        self.assertEqual(snapshot, expected)
        for session, socket in [('owner', 'two'), ('other', 'one'), ('other', 'two')]:
            with self.subTest(session=session, socket=socket):
                self.assertEqual(self.controller.status_for(session, socket),
                                 {'available': True, 'connected': True, 'lease': None})
        snapshot['lease'] = None
        snapshot['connected'] = False
        self.assertEqual(self.controller.status_for('owner', 'one'), expected)
        self.assertEqual(self.controller.status, {
            'available': True, 'connected': True, 'controlled': True, 'dragging': False})

    async def test_status_for_reports_native_stop_and_successor_without_backend(self):
        await self.controller.close()
        self.controller = Controller(clock=lambda: self.now)
        self.addAsyncCleanup(self.controller.close)
        empty = {'available': False, 'connected': False, 'lease': None}
        self.assertEqual(self.controller.status_for('owner', 'one'), empty)
        lease = await self.own()
        held = {'available': False, 'connected': False, 'lease': lease}
        self.assertEqual(self.controller.status_for('owner', 'one'), held)
        self.controller.stop('native_stop')
        self.assertEqual(self.controller.status_for('owner', 'one'), empty)
        successor = await self.send('take_control', socket='two')
        self.assertNotEqual(successor['lease'], lease)
        self.assertEqual(self.controller.status_for('owner', 'one'), empty)
        self.assertEqual(self.controller.status_for('owner', 'two'),
                         {'available': False, 'connected': False, 'lease': successor['lease']})

    async def test_status_for_reads_do_not_renew_lease(self):
        lease = await self.own()
        self.now += 2
        self.assertEqual(self.controller.status_for('owner', 'one')['lease'], lease)
        self.now += 1
        self.controller.tick()
        self.assertEqual(self.controller.status_for('owner', 'one'),
                         {'available': True, 'connected': False, 'lease': None})

    async def test_owner_is_session_socket_and_random_generation(self):
        lease = await self.own()
        self.assertEqual((await self.send('take_control', socket='two'))['error'], 'busy')
        for kind in ('heartbeat', 'release_control', 'connect', 'press'):
            for session, socket in [('owner', 'two'), ('other', 'one')]:
                result = await self.send(kind, session=session, socket=socket, lease=lease)
                self.assertEqual(result['error'], 'not_owner')
        self.controller.socket_closed('two')
        self.seq['two'] = 0
        self.assertTrue(self.controller.status['controlled'])
        await self.send('release_control', lease=lease)
        new = await self.send('take_control', socket='two')
        self.assertNotEqual(new['lease'], lease)
        self.controller.socket_closed('one')
        self.assertTrue(self.controller.status['controlled'])
        self.assertEqual((await self.send('heartbeat', socket='two', lease=lease))['error'], 'not_owner')

    async def test_exact_schema_numbers_and_closed_key_enum(self):
        lease = await self.connected()
        invalid = [
            {'kind': 'shell', 'text': 'ignored'}, {'kind': 'key', 'key': 'POWER'},
            {'kind': 'key', 'key': []}, {'kind': 'move', 'dx': True, 'dy': 0},
            {'kind': 'move', 'dx': 257, 'dy': 0}, {'kind': 'move', 'dx': 1.0, 'dy': 0},
            {'kind': 'move', 'dx': float('nan'), 'dy': 0},
            {'kind': 'scroll', 'dx': 0, 'dy': -2}, {'kind': 'tap', 'extra': 1},
            {'kind': 'connect', 'target': 'forbidden'}, {'kind': []},
        ]
        for fields in invalid:
            result = await self.send(lease=lease, **fields)
            self.assertEqual(result['error'], 'schema', fields)
        for key in ('UP', 'DOWN', 'LEFT', 'RIGHT', 'OK', 'HOME', 'BACK',
                    'VOLUME_UP', 'VOLUME_DOWN', 'PLAY_PAUSE'):
            self.assertEqual((await self.send('key', key=key, lease=lease))['status'], 'admitted')
            await self.settle()
        self.assertEqual([fields['key'] for kind, fields in self.sent if kind == 'key'],
                         ['UP', 'DOWN', 'LEFT', 'RIGHT', 'OK', 'HOME', 'BACK',
                          'VOLUME_UP', 'VOLUME_DOWN', 'PLAY_PAUSE'])

    async def test_sequence_gap_replay_bool_and_reset_on_disconnect(self):
        for seq in (0, True, 1.0, 2):
            result = await self.controller.handle('owner', 'one', {
                'version': 1, 'seq': seq, 'kind': 'take_control'})
            self.assertEqual(result['error'], 'sequence')
        lease = await self.own()
        result = await self.controller.handle('owner', 'one', {
            'version': 1, 'seq': 1, 'kind': 'heartbeat', 'lease': lease})
        self.assertEqual(result['error'], 'sequence')
        self.controller.socket_closed('one')
        self.seq['one'] = 0
        await self.own()

    async def test_version_mismatch_and_observer_stop_fence_immediately(self):
        for version in (2, True):
            lease = await self.connected()
            await self.send('press', lease=lease)
            await self.settle()
            count = len(self.fences)
            result = await self.controller.handle('observer', 'two', {
                'version': version, 'seq': 1, 'kind': 'stop'})
            self.assertEqual(result['error'], 'version')
            self.assertEqual(len(self.fences), count + 1)
            self.assertFalse(self.controller.status['controlled'])
            self.assertFalse(self.controller.status['dragging'])
            await self.settle()
        await self.connected()
        result = await self.send('stop', session='observer', socket='three')
        self.assertEqual(result['status'], 'admitted')
        self.assertFalse(self.controller.status['connected'])

    async def test_heartbeat_expiry_cannot_be_extended_by_input_or_wrong_tab(self):
        lease = await self.connected()
        self.now += 2.9
        await self.send('key', key='HOME', lease=lease)
        await self.send('heartbeat', socket='two', lease=lease)
        self.now += .1
        self.controller.tick()
        self.assertFalse(self.controller.status['controlled'])
        self.assertEqual((await self.send('heartbeat', lease=lease))['error'], 'not_owner')

    async def test_owner_heartbeats_renew_until_disconnect_or_revocation(self):
        for end in ('disconnect', 'revoke'):
            lease = await self.connected()
            self.now += 2
            await self.send('heartbeat', lease=lease)
            self.now += 2
            self.controller.tick()
            self.assertTrue(self.controller.status['controlled'])
            self.controller.revoke('other')
            self.assertTrue(self.controller.status['controlled'])
            if end == 'disconnect':
                self.controller.socket_closed('one')
                self.seq['one'] = 0
            else:
                self.controller.revoke('owner')
            self.assertFalse(self.controller.status['controlled'])
            await self.settle()

    async def test_drag_idle_and_total_deadlines(self):
        lease = await self.connected()
        await self.send('press', lease=lease)
        await self.settle()
        self.now += 2
        self.controller.tick()
        self.assertFalse(self.controller.status['controlled'])
        await self.settle()
        lease = await self.connected()
        await self.send('press', lease=lease)
        for _ in range(15):
            self.now += 1
            await self.send('heartbeat', lease=lease)
            await self.send('move', dx=1, dy=0, lease=lease)
            await self.settle()
        self.controller.tick()
        self.assertFalse(self.controller.status['dragging'])
        self.assertFalse(self.controller.status['controlled'])

    async def test_motion_coalesces_bounded_axes_and_release_precedes_pending_motion(self):
        lease = await self.connected()
        await self.send('press', lease=lease)
        await self.settle()
        await self.send('move', dx=1, dy=0, lease=lease)
        await self.settle()
        for _ in range(100):
            await self.send('move', dx=256, dy=-256, lease=lease)
        await self.settle()
        self.assertEqual(sum(kind == 'move' for kind, _ in self.sent), 1)
        self.now += 1 / 30 + .001
        self.controller.tick()
        await self.settle()
        self.assertEqual(self.sent[-1], ('move', {'dx': 256, 'dy': -256}))
        await self.send('move', dx=7, dy=0, lease=lease)
        await self.send('release', lease=lease)
        await self.settle()
        self.assertEqual(self.sent[-1], ('release', {}))
        self.assertFalse(self.controller.status['dragging'])
        self.now += .1
        self.controller.tick()
        await self.settle()
        self.assertEqual(self.sent[-1], ('release', {}))

    async def assert_motion_around_button(self, kind):
        lease = await self.connected()
        await self.send('move', dx=1, dy=0, lease=lease)
        await self.settle()
        initial = list(self.sent)
        for action, fields in [('move', {'dx': 7, 'dy': -2}), (kind, {}),
                               ('move', {'dx': 11, 'dy': 3})]:
            self.assertEqual((await self.send(action, lease=lease, **fields))['status'], 'admitted')
        await self.settle()
        self.assertEqual(self.sent, initial)  # Button waits for the rate-limited prior move.
        self.now += 1 / 30 + .001
        self.controller.tick()
        await self.settle()
        expected = initial + [('move', {'dx': 7, 'dy': -2}), (kind, {})]
        self.assertEqual(self.sent, expected)
        self.now += 1 / 30 + .001
        self.controller.tick()
        await self.settle()
        self.assertEqual(self.sent, expected + [('move', {'dx': 11, 'dy': 3})])

    async def test_motion_before_tap_and_after_tap_remains_ordered(self):
        await self.assert_motion_around_button('tap')

    async def test_motion_before_press_and_after_press_remains_ordered(self):
        await self.assert_motion_around_button('press')

    async def test_motion_at_button_boundaries_shares_eight_entry_queue_limit(self):
        lease = await self.connected()
        for _ in range(4):
            await self.send('move', dx=1, dy=0, lease=lease)
            self.assertEqual((await self.send('tap', lease=lease))['status'], 'admitted')
        await self.send('move', dx=1, dy=0, lease=lease)
        self.assertEqual((await self.send('tap', lease=lease)).get('error'), 'overflow')
        self.assertTrue(self.fences)
        await self.settle()
        self.assertEqual(self.sent, [('connect', {}), ('stop', {})])

    async def test_release_clears_motion_waiting_before_button(self):
        lease = await self.connected()
        await self.send('move', dx=1, dy=0, lease=lease)
        await self.settle()
        initial = list(self.sent)
        await self.send('move', dx=7, dy=0, lease=lease)
        await self.send('press', lease=lease)
        await self.settle()
        await self.send('release', lease=lease)
        await self.settle()
        self.assertEqual(self.sent, initial + [('release', {})])
        self.now += .1
        self.controller.tick()
        await self.settle()
        self.assertEqual(self.sent, initial + [('release', {})])

    async def test_stop_clears_motion_waiting_before_button(self):
        lease = await self.connected()
        await self.send('move', dx=1, dy=0, lease=lease)
        await self.settle()
        initial = list(self.sent)
        await self.send('move', dx=7, dy=0, lease=lease)
        await self.send('tap', lease=lease)
        await self.settle()
        self.controller.stop('native')
        self.assertEqual(self.fences, [len(initial)])
        await self.settle()
        self.assertEqual(self.sent, initial + [('stop', {})])

    async def test_eight_pending_controls_overflow_fences_and_discards(self):
        lease = await self.connected()
        started, hold = asyncio.Event(), asyncio.Event()
        async def blocked(kind, fields):
            if kind == 'key':
                started.set()
                await hold.wait()
            await self.record(kind, fields)
        self.behavior = blocked
        await self.send('key', key='HOME', lease=lease)
        await started.wait()
        for _ in range(8):
            self.assertEqual((await self.send('tap', lease=lease))['status'], 'admitted')
        self.assertEqual((await self.send('tap', lease=lease))['error'], 'overflow')
        self.assertTrue(self.fences)
        await self.settle()
        self.assertFalse(any(kind in ('key', 'tap') for kind, _ in self.sent))

    async def test_late_connect_cannot_publish_or_stop_successor(self):
        started, cancelled, finish = asyncio.Event(), asyncio.Event(), asyncio.Event()
        first = True
        async def delayed(kind, fields):
            nonlocal first
            if kind == 'connect' and first:
                first = False
                started.set()
                try:
                    await finish.wait()
                except asyncio.CancelledError:
                    cancelled.set()
                    await finish.wait()
            await self.record(kind, fields)
        await self.controller.close()
        self.sent.clear()
        self.controller = Controller(delayed, lambda: self.fences.append(len(self.sent)), clock=lambda: self.now)
        self.addAsyncCleanup(self.controller.close)
        lease = await self.own()
        await self.send('connect', lease=lease)
        await started.wait()
        self.controller.stop('native')
        await cancelled.wait()
        replacement = await self.send('take_control', socket='two')
        await self.send('connect', socket='two', lease=replacement['lease'])
        self.assertFalse(self.controller.status['connected'])
        finish.set()
        await self.settle()
        self.assertTrue(self.controller.status['connected'])
        self.assertEqual([kind for kind, _ in self.sent], ['connect', 'stop', 'connect'])
        self.controller.socket_closed('one')
        self.assertTrue(self.controller.status['connected'])

    async def test_slow_dispatch_and_channel_failure_fail_closed(self):
        for failure in ('slow', 'exception'):
            lease = await self.connected()
            stopped = asyncio.Event()
            async def broken(kind, fields):
                if kind == 'key':
                    if failure == 'exception':
                        raise OSError('private backend details')
                    await asyncio.Event().wait()
                if kind == 'stop':
                    stopped.set()
            self.behavior = broken
            await self.send('key', key='HOME', lease=lease)
            await asyncio.wait_for(stopped.wait(), 1)
            self.assertFalse(self.controller.status['connected'])
            self.assertFalse(self.controller.status['controlled'])
            self.behavior = self.record
            await self.settle()

    async def test_backend_self_cancellation_invalidates_lease(self):
        lease = await self.connected()
        async def cancelled(kind, fields):
            if kind == 'key':
                raise asyncio.CancelledError()
            await self.record(kind, fields)
        self.behavior = cancelled
        await self.send('key', key='HOME', lease=lease)
        await self.settle()
        self.assertFalse(self.controller.status['connected'])
        self.assertFalse(self.controller.status['controlled'])
        self.assertTrue(self.fences)

    async def test_failed_old_cleanup_blocks_successor_reconnect(self):
        await self.connected()
        started, finish = asyncio.Event(), asyncio.Event()
        async def failed_cleanup(kind, fields):
            if kind == 'stop':
                started.set()
                await finish.wait()
                raise OSError('cleanup uncertain')
            await self.record(kind, fields)
        self.behavior = failed_cleanup
        self.controller.stop('native')
        await started.wait()
        replacement = await self.send('take_control', socket='two')
        await self.send('connect', socket='two', lease=replacement['lease'])
        finish.set()
        await self.settle()
        self.assertFalse(self.controller.status['available'])
        self.assertFalse(self.controller.status['controlled'])
        self.assertFalse(self.controller.status['connected'])
        self.assertEqual([kind for kind, _ in self.sent], ['connect'])
        self.assertEqual((await self.send('take_control'))['error'], 'unavailable')

    async def test_release_bypasses_queued_controls_and_motion(self):
        lease = await self.connected()
        await self.send('press', lease=lease)
        await self.settle()
        entered, finish = asyncio.Event(), asyncio.Event()
        async def blocked(kind, fields):
            if kind == 'move':
                entered.set()
                await finish.wait()
            await self.record(kind, fields)
        self.behavior = blocked
        await self.send('move', dx=1, dy=0, lease=lease)
        await entered.wait()
        await self.send('key', key='HOME', lease=lease)
        await self.send('move', dx=2, dy=0, lease=lease)
        await self.send('release', lease=lease)
        finish.set()
        await self.settle()
        self.assertEqual([kind for kind, _ in self.sent], ['connect', 'press', 'move', 'release'])

    async def test_native_fence_is_synchronous_and_precedes_cleanup(self):
        lease = await self.connected()
        await self.send('press', lease=lease)
        await self.settle()
        await self.send('key', key='HOME', lease=lease)
        sent_before = list(self.sent)
        self.controller.stop('native')
        self.assertEqual(self.fences, [len(sent_before)])
        self.assertEqual(self.sent, sent_before)
        await self.settle()
        self.assertEqual(self.sent, sent_before + [('stop', {})])

    async def test_disconnected_and_drag_state_do_not_admit_invalid_actions(self):
        lease = await self.own()
        self.assertEqual((await self.send('tap', lease=lease))['error'], 'disconnected')
        await self.send('connect', lease=lease)
        self.assertEqual((await self.send('connect', lease=lease))['error'], 'state')
        await self.settle()
        await self.send('press', lease=lease)
        for kind, fields in [('press', {}), ('tap', {}), ('scroll', {'dx': 0, 'dy': 1})]:
            self.assertEqual((await self.send(kind, lease=lease, **fields))['error'], 'state')
        await self.send('release', lease=lease)
        await self.settle()
        for kind, fields in [('tap', {}), ('scroll', {'dx': -1, 'dy': 1}), ('netflix', {})]:
            self.assertEqual((await self.send(kind, lease=lease, **fields))['status'], 'admitted')
            await self.settle()
        self.assertEqual(self.sent[-3:], [('tap', {}), ('scroll', {'dx': -1, 'dy': 1}), ('netflix', {})])

    async def test_close_fences_rejects_new_work_and_finishes_cleanup(self):
        await self.connected()
        await self.controller.close()
        self.assertEqual(self.sent[-1], ('stop', {}))
        self.assertEqual((await self.send('take_control'))['error'], 'closed')
        self.assertFalse(self.controller.status['connected'])


if __name__ == '__main__':
    unittest.main()
