"""Exercise dispatch lifecycle, independent of Tk rendering and real devices."""
import asyncio
import threading
import unittest
from unittest.mock import patch

from app import RemoteWindow


class Status:
    def set(self, value):
        self.value = value


class Root:
    def after(self, *_args):
        pass


class Device:
    connected = False
    def __init__(self):
        self.pending = None
        self.started = asyncio.Event()
        self.keys = []
    async def start_pairing(self):
        self.pending = asyncio.current_task()
        self.started.set()
        await asyncio.Event().wait()
    async def reconnect(self):
        self.connected = True
    def disconnect(self):
        self.connected = False
        if self.pending:
            self.pending.cancel()
    def key(self, name):
        if not self.connected:
            raise ConnectionError('TV disconnected')
        self.keys.append(name)
    def launch_netflix(self):
        self.key('NETFLIX')


class Pad:
    def __init__(self):
        self.connected = True
        self.down = False
        self.events = []
        self.closed = threading.Event()
        self.stopping = False
    def move(self, *deltas):
        self.events.append(('move', *deltas))
    def press(self):
        self.down = True
        self.events.append(('press',))
    def release(self):
        self.down = False
        self.events.append(('release',))
    def scroll(self, *direction):
        self.events.append(('scroll', *direction))
    def close(self):
        self.connected = self.down = False
        self.closed.set()
    def request_stop(self):
        self.stopping = True


def dispatcher(loop):
    # Construct only dispatcher state; never create Tk widgets or device connections.
    w = RemoteWindow.__new__(RemoteWindow)
    w.loop = loop
    w.queue = asyncio.Queue(maxsize=8)
    w.tv = Device()
    w.pad = None
    w.serial = 'test-device'
    w.epoch = 0
    w.pending = [0., 0.]
    w.status = Status()
    w.channel = Status()
    w.root = Root()
    w.tv_established = w.pad_established = False
    w.closing = w.busy = w.setting_up = False
    w.tv_task = None
    return w


class DispatchTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.window = dispatcher(asyncio.get_running_loop())
        w = self.window
        self.consumer = w.loop.create_task(w.consume())

    async def asyncTearDown(self):
        self.window.closing = True
        self.consumer.cancel()
        await asyncio.gather(self.consumer, return_exceptions=True)

    async def idle(self):
        async with asyncio.timeout(1):
            while self.window.busy or not self.window.queue.empty():
                await asyncio.sleep(0)

    async def test_idle_channel_loss_stops_both_and_clears_pending_input(self):
        w = self.window
        for lost in ('tv', 'pad'):
            with self.subTest(lost=lost):
                w.enqueue('reconnect')
                await self.idle()
                pad = Pad()
                with patch('app.pointer.start', return_value=pad):
                    w.enqueue('pointer')
                    await self.idle()
                epoch = w.epoch
                getattr(w, lost).connected = False
                w.enqueue('key', 'OK')
                w.pending[:] = [10., 20.]
                w.refresh()
                self.assertEqual(w.epoch, epoch + 1)
                self.assertFalse(w.tv.connected)
                self.assertIsNone(w.pad)
                self.assertTrue(pad.stopping)
                self.assertTrue(w.queue.empty())
                self.assertEqual(w.pending, [0., 0.])
                self.assertIn('TV remote' if lost == 'tv' else 'Touchpad', w.status.value)
                self.assertTrue(await asyncio.to_thread(pad.closed.wait, 1))
                w.refresh()  # Stop cleared expectations; do not repeatedly stop.
                self.assertEqual(w.epoch, epoch + 1)
                self.assertEqual(w.tv.keys, [])

    async def test_confirm_success_establishes_loss_monitoring(self):
        w = self.window
        async def confirm(_code):
            w.tv.connected = True
        with patch.object(w.tv, 'finish_pairing', confirm, create=True):
            w.enqueue('confirm', 'ABCDEF')
            await self.idle()
        w.tv.connected = False
        w.refresh()
        self.assertEqual(w.epoch, 1)
        self.assertIn('TV remote', w.status.value)

    async def test_intentional_tv_setup_does_not_stop_other_channel(self):
        w = self.window
        w.refresh()  # Neither channel has been established yet.
        self.assertEqual(w.epoch, 0)
        pad = Pad()
        with patch('app.pointer.start', return_value=pad):
            w.enqueue('pointer')
            await self.idle()
        for action, method in [('pair', 'start_pairing'), ('reconnect', 'reconnect')]:
            with self.subTest(action=action):
                w.enqueue('reconnect')
                await self.idle()
                started, proceed = asyncio.Event(), asyncio.Event()
                async def setup():
                    w.tv.connected = False
                    started.set()
                    await proceed.wait()
                    if action == 'reconnect':
                        w.tv.connected = True
                with patch.object(w.tv, method, setup):
                    w.enqueue(action)
                    await asyncio.wait_for(started.wait(), 1)
                    try:
                        w.refresh()
                        self.assertEqual(w.epoch, 0)
                    finally:
                        proceed.set()
                    await self.idle()
                w.refresh()  # Pair waits for a code while intentionally disconnected.
                self.assertEqual(w.epoch, 0)
                self.assertIs(w.pad, pad)
                self.assertFalse(pad.stopping)

    async def test_stop_during_old_pointer_cleanup_prevents_replacement_start(self):
        w = self.window
        w.enqueue('reconnect')
        await self.idle()
        started, proceed = threading.Event(), threading.Event()
        class OldPad(Pad):
            def close(self):
                super().close()
                started.set()
                if not proceed.wait(1):
                    raise TimeoutError('Test did not release cleanup')
        old = OldPad()
        with patch('app.pointer.start', return_value=old):
            w.enqueue('pointer')
            await self.idle()
        replacements = []
        def start(_serial):
            replacement = Pad()
            replacements.append(replacement)
            return replacement
        with patch('app.pointer.start', start):
            w.enqueue('pointer')
            self.assertTrue(await asyncio.to_thread(started.wait, 1))
            try:
                w.refresh()  # Old transport closing is an intentional transition.
                self.assertEqual(w.epoch, 0)
                self.assertTrue(w.tv.connected)
                w.stop()
                self.assertTrue(old.stopping)
            finally:
                proceed.set()
            await self.idle()
        self.assertEqual(replacements, [])
        self.assertIsNone(w.pad)
        w.refresh()
        self.assertEqual(w.epoch, 1)

    async def test_controls_during_queued_and_running_tv_setup_are_not_replayed(self):
        w = self.window
        for action, method, args in [('pair', 'start_pairing', ()),
                                     ('confirm', 'finish_pairing', ('ABCDEF',)),
                                     ('reconnect', 'reconnect', ())]:
            with self.subTest(action=action):
                started, proceed = asyncio.Event(), asyncio.Event()
                async def setup(*_args):
                    w.tv.connected = False
                    started.set()
                    await proceed.wait()
                    w.tv.connected = True
                w.tv.keys.clear()
                w.pad = Pad()
                with patch.object(w.tv, method, setup, create=True):
                    w.enqueue(action, *args)
                    w.enqueue('key', 'OK')  # Before the setup task even starts.
                    await asyncio.wait_for(started.wait(), 1)
                    w.enqueue('netflix')
                    w.enqueue('press')
                    w.enqueue('scroll', 0, -1)
                    proceed.set()
                    await self.idle()
                self.assertEqual(w.tv.keys, [])
                self.assertEqual(w.pad.events, [])
                w.enqueue('key', 'HOME')
                await self.idle()
                self.assertEqual(w.tv.keys, ['HOME'])

    async def test_controls_during_pointer_startup_are_not_replayed(self):
        w = self.window
        w.tv.connected = True
        started, proceed = threading.Event(), threading.Event()
        pad = Pad()
        def start(_serial):
            started.set()
            if not proceed.wait(1):
                raise TimeoutError('Test did not release startup')
            return pad
        with patch('app.pointer.start', start):
            w.enqueue('pointer')
            w.enqueue('press')
            self.assertTrue(await asyncio.to_thread(started.wait, 1))
            w.enqueue('key', 'OK')
            w.enqueue('scroll', 0, -1)
            proceed.set()
            await self.idle()
        self.assertEqual(pad.events, [('move', 0, 0)])
        self.assertEqual(w.tv.keys, [])

    async def test_setup_releases_existing_drag_before_waiting(self):
        w = self.window
        w.pad = Pad()
        w.pad.press()
        w.enqueue('pair')
        await asyncio.wait_for(w.tv.started.wait(), 1)
        self.assertFalse(w.pad.down)
        self.assertEqual(w.pad.events, [('press',), ('release',)])
        w.stop()

    async def test_tv_failure_stops_drag_and_discards_queued_reconnect_and_input(self):
        w = self.window
        pad = w.pad = Pad()
        pad.press()
        w.pending[:] = [10., 20.]
        w.enqueue('key', 'HOME')  # TV is disconnected: real adapter raises here.
        w.enqueue('reconnect')
        w.enqueue('key', 'OK')
        await self.idle()
        self.assertFalse(w.tv.connected)
        self.assertIsNone(w.pad)
        self.assertTrue(await asyncio.to_thread(pad.closed.wait, 1))
        self.assertFalse(pad.down)
        self.assertEqual(w.epoch, 1)
        self.assertEqual(w.pending, [0., 0.])
        self.assertEqual(w.tv.keys, [])
        self.assertIn('Key failed (ConnectionError)', w.status.value)

    async def test_pointer_startup_failure_stops_tv_without_repair_advice_or_replay(self):
        w = self.window
        w.enqueue('reconnect')
        await self.idle()
        with patch('app.pointer.start', side_effect=ConnectionError('private endpoint detail')):
            w.pending[:] = [10., 20.]
            w.enqueue('pointer')
            w.enqueue('key', 'OK')
            await self.idle()
        self.assertFalse(w.tv.connected)
        self.assertIsNone(w.pad)
        self.assertEqual(w.epoch, 1)
        self.assertTrue(w.queue.empty())
        self.assertEqual(w.pending, [0., 0.])
        self.assertEqual(w.tv.keys, [])
        self.assertIn('Touchpad', w.status.value)
        self.assertIn('ADB', w.status.value)
        self.assertIn('Touchpad connection failed; both channels stopped.', w.status.value)
        self.assertIn('address', w.status.value)
        self.assertIn('Reconnect TV', w.status.value)
        self.assertIn('awake', w.status.value)
        self.assertIn('same Wi-Fi', w.status.value)
        self.assertNotIn('pair', w.status.value.lower())
        self.assertNotIn('private endpoint detail', w.status.value)
        w.enqueue('reconnect')
        await self.idle()
        self.assertTrue(w.tv.connected)
        self.assertEqual(w.tv.keys, [])

    async def test_pointer_failure_disconnects_tv_and_discards_queued_input(self):
        w = self.window
        w.tv.connected = True
        pad = w.pad = Pad()
        with patch.object(pad, 'move', side_effect=ConnectionError('offline')):
            w.enqueue('move', 1, 2)
            w.enqueue('key', 'OK')
            await self.idle()
        self.assertFalse(w.tv.connected)
        self.assertIsNone(w.pad)
        self.assertTrue(await asyncio.to_thread(pad.closed.wait, 1))
        self.assertEqual(w.epoch, 1)
        self.assertEqual(w.tv.keys, [])
        self.assertIn('Move failed (ConnectionError)', w.status.value)

    async def test_stop_during_pairing_preserves_dispatcher_and_discards_queued_keys(self):
        w = self.window
        w.enqueue('pair')
        await asyncio.wait_for(w.tv.started.wait(), 1)
        w.enqueue('key', 'OK')
        w.stop()
        for _ in range(10):
            await asyncio.sleep(0)
        self.assertFalse(self.consumer.done())
        self.assertEqual(w.tv.keys, [])
        w.enqueue('reconnect')
        for _ in range(10):
            await asyncio.sleep(0)
        self.assertTrue(w.tv.connected)

    async def test_overflow_stops_and_clears_input(self):
        w = self.window
        for _ in range(9):
            w.enqueue('key', 'OK')
        self.assertTrue(w.queue.empty())
        self.assertEqual(w.epoch, 1)
        await asyncio.sleep(0)
        self.assertEqual(w.tv.keys, [])

    async def test_stop_fences_pointer_before_cleanup_worker_runs(self):
        w = self.window
        pad = w.pad = Pad()
        w.stop()
        self.assertTrue(pad.stopping)
        self.assertTrue(await asyncio.to_thread(pad.closed.wait, 1))

    async def test_stop_while_pointer_starts_disposes_new_session(self):
        w = self.window
        started, proceed, closed = threading.Event(), threading.Event(), threading.Event()
        class Pad:
            def close(self):
                closed.set()
        def start(_serial):
            started.set()
            proceed.wait(1)
            return Pad()
        with patch('app.pointer.start', start):
            w.enqueue('pointer')
            self.assertTrue(await asyncio.to_thread(started.wait, 1))
            w.stop()
            proceed.set()
            self.assertTrue(await asyncio.to_thread(closed.wait, 1))
        self.assertIsNone(w.pad)


class PumpStopTests(unittest.TestCase):
    def test_stop_between_pump_ticks_cancels_tv_child_before_it_starts(self):
        for action, method, args in [('pair', 'start_pairing', ()),
                                     ('confirm', 'finish_pairing', ('ABCDEF',)),
                                     ('reconnect', 'reconnect', ())]:
            with self.subTest(action=action):
                loop = asyncio.new_event_loop()
                w = dispatcher(loop)
                started = []
                async def operation(*_args):
                    started.append(action)
                    await asyncio.Event().wait()
                consumer = loop.create_task(w.consume())
                try:
                    with patch.object(w.tv, method, operation, create=True):
                        w.enqueue(action, *args)
                        # Same bounded event-loop tick as the Tk pump.
                        loop.call_soon(loop.stop)
                        loop.run_forever()
                        self.assertTrue(w.busy)
                        self.assertEqual(started, [])
                        w.stop()
                        for _ in range(4):
                            loop.call_soon(loop.stop)
                            loop.run_forever()
                        self.assertEqual(started, [])
                        self.assertFalse(w.busy)
                        self.assertFalse(consumer.done())
                finally:
                    w.closing = True
                    consumer.cancel()
                    loop.run_until_complete(asyncio.gather(consumer, return_exceptions=True))
                    loop.run_until_complete(loop.shutdown_default_executor())
                    loop.close()


if __name__ == '__main__':
    unittest.main()
