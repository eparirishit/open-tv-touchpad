"""Wire and lifecycle tests for the disposable scrcpy proof."""
import hashlib
import socket
import struct
import subprocess
import threading
import unittest
from unittest.mock import Mock, patch

import pointer


class PointerTests(unittest.TestCase):
    def setUp(self):
        self.sender, self.reader = socket.socketpair()
        self.reader.settimeout(.1)
        self.pad = pointer.Pointer(self.sender, 1920, 1080)

    def tearDown(self):
        self.pad.close()
        self.reader.close()

    def event(self):
        result = bytearray()
        while len(result) < 32:
            chunk = self.reader.recv(32 - len(result))
            if not chunk:
                self.fail('Unexpected EOF in touch event')
            result.extend(chunk)
        return struct.unpack('>BBQiiHHHII', result)

    def test_event_reader_fails_on_eof(self):
        self.pad.close()
        with self.assertRaisesRegex(AssertionError, 'Unexpected EOF'):
            self.event()

    def test_hover_has_raw_display_coordinates_and_mouse_identity(self):
        self.pad.move(10, -5)
        self.assertEqual(self.event(), (2, 7, 0xffffffffffffffff,
                         970, 535, 1920, 1080, 0, 0, 0))

    def test_movement_is_clamped_to_display(self):
        for _ in range(20):
            self.pad.move(-256, 256)
            event = self.event()
        self.assertEqual(event[3:5], (0, 1079))

    def test_invalid_delta_emits_nothing(self):
        for value in (float('nan'), float('inf'), 257, '1', True):
            with self.assertRaises(ValueError):
                self.pad.move(value, 0)
        with self.assertRaises(TimeoutError):
            self.reader.recv(1)

    def test_drag_sequence_and_close_release(self):
        self.pad.press()
        self.assertEqual(self.event()[1], 0)
        self.pad.move(10, 20)
        self.assertEqual(self.event()[1], 2)
        self.pad.close()
        event = self.event()
        self.assertEqual((event[1], event[7]), (1, 0))
        self.assertEqual(self.reader.recv(1), b'')
        with self.assertRaises(ConnectionError):
            self.pad.press()

    def test_tap_releases_before_restoring_hover(self):
        self.pad.tap()
        events = [self.event() for _ in range(3)]
        self.assertEqual([e[1] for e in events], [0, 1, 7])
        self.assertEqual([e[7] for e in events], [65535, 0, 0])

    def test_scroll_is_touch_swipe_and_restores_cursor(self):
        self.pad.scroll(0, -1)
        events = [self.event() for _ in range(10)]
        self.assertEqual([e[1] for e in events], [0] + [2] * 7 + [1, 7])
        self.assertEqual(events[-2][3:5], (960, 380))
        self.assertEqual(events[-1][3:5], (960, 540))

    def test_disconnect_during_gesture_does_not_replay(self):
        self.pad.press()
        self.event()
        self.reader.close()
        with self.assertRaises(ConnectionError):
            self.pad.move(2, 3)
        self.assertFalse(self.pad.connected)

    def test_idle_drag_expires_with_release(self):
        with patch('pointer.time.monotonic', return_value=100):
            self.pad.press()
        self.event()
        with patch('pointer.time.monotonic', return_value=102.1):
            self.pad.expire()
        self.assertEqual(self.event()[1], 1)
        self.assertFalse(self.pad.connected)

    def test_continuous_drag_has_total_deadline(self):
        with patch('pointer.time.monotonic', return_value=100):
            self.pad.press()
        self.event()
        with patch('pointer.time.monotonic', return_value=114.9):
            self.pad.move(1, 0)
        self.event()
        with patch('pointer.time.monotonic', return_value=115.1):
            self.pad.expire()
        self.assertEqual(self.event()[1], 1)
        self.assertFalse(self.pad.connected)


class HardwarePointerTests(unittest.TestCase):
    def setUp(self):
        self.sender, self.reader = socket.socketpair()
        self.reader.settimeout(.1)
        self.pad = pointer.HardwarePointer(self.sender, 1920, 1080)
        create = self.reader.recv(256)
        self.assertEqual(create[:7], bytes.fromhex('0c000100000000'))

    def tearDown(self):
        self.pad.close()
        self.reader.close()

    def read_bytes(self, size):
        result = bytearray()
        while len(result) < size:
            chunk = self.reader.recv(size - len(result))
            if not chunk:
                self.fail('Unexpected EOF in HID message')
            result.extend(chunk)
        return bytes(result)

    def test_reader_fails_on_partial_message_eof(self):
        self.pad.close()  # Three-byte destroy, then EOF.
        with self.assertRaisesRegex(AssertionError, 'Unexpected EOF'):
            self.read_bytes(4)

    def test_relative_motion_is_mouse_report_not_sdk_coordinates(self):
        self.pad.move(5, -4)
        self.assertEqual(self.read_bytes(10), bytes.fromhex('0d000100050005fc0000'))

    def test_large_motion_splits_without_wrapping_or_losing_remainder(self):
        self.pad.move(256, -130)
        reports = [self.read_bytes(10) for _ in range(3)]
        self.assertEqual([struct.unpack('>Bbbbb', r[5:]) for r in reports],
                         [(0, 127, -127, 0, 0), (0, 127, -3, 0, 0), (0, 2, 0, 0, 0)])

    def test_close_releases_buttons_then_destroys_mouse(self):
        self.pad.press()
        self.assertEqual(self.read_bytes(10)[5], 1)
        self.pad.close()
        self.assertEqual(self.read_bytes(13), bytes.fromhex('0d0001000500000000000e0001'))

    def test_scroll_uses_two_mouse_wheels_without_guessing_cursor_coordinates(self):
        self.pad.scroll(-1, -1)
        self.assertEqual(self.read_bytes(10), bytes.fromhex('0d00010005000000ff01'))


class StartupTests(unittest.TestCase):
    def setUp(self):
        self.states = ['device']
        self.model = 'XK03H'
        self.connect_error = None
        self.commands = []
        server = b'test-server'
        self.enterContext(patch('pointer.SERVER', Mock(read_bytes=Mock(return_value=server))))
        self.enterContext(patch('pointer.SERVER_SHA256', hashlib.sha256(server).hexdigest()))
        self.enterContext(patch('pointer.subprocess.run', side_effect=self.run_adb))
        self.process = self.enterContext(patch('pointer.subprocess.Popen'))
        self.process.return_value.poll.return_value = None
        self.connection = Mock()
        self.connection.recv.return_value = b'\0'
        self.enterContext(patch('pointer.socket.create_connection', return_value=self.connection))
        self.enterContext(patch('pointer.threading.Thread'))

    def run_adb(self, argv, *, timeout, **_kwargs):
        self.assertEqual(argv[:6], ['adb', '-H', '127.0.0.1', '-P', '5037', '-s'])
        self.assertGreater(timeout, 0)
        self.assertLessEqual(timeout, 8)
        command = tuple(argv[7:])
        self.commands.append((argv[6], command))
        if command == ('get-state',):
            state = self.states.pop(0)
            return subprocess.CompletedProcess(argv, int(state == 'missing'), stdout=state)
        if command[0] == 'connect':
            self.assertEqual(command, ('connect', argv[6]))
            if self.connect_error:
                raise self.connect_error
            output = 'connect response is not proof of readiness'
        elif command == ('shell', 'getprop', 'ro.product.model'):
            output = self.model
        elif command == ('shell', 'wm', 'size'):
            output = 'Physical size: 1920x1080'
        elif command[:2] == ('forward', 'tcp:0'):
            output = '27183'
        elif command[0] == 'push' or command[:2] == ('forward', '--remove') or command[:3] == ('shell', 'rm', '-f'):
            output = ''
        else:
            self.fail('Unexpected ADB command')
        return subprocess.CompletedProcess(argv, 0, stdout=output)

    def start(self, serial):
        pad = pointer.start(serial)
        self.addCleanup(pad.close)
        return pad

    def test_connected_transport_is_preserved_without_connect(self):
        self.start('USB-serial')
        self.assertEqual(self.commands[:2], [
            ('USB-serial', ('get-state',)),
            ('USB-serial', ('shell', 'getprop', 'ro.product.model'))])
        self.assertFalse(any(c[0] == 'connect' for _, c in self.commands))

    def test_missing_transport_connects_exact_endpoint_once_before_model_and_push(self):
        for endpoint in ('192.168.1.42:5555', '10.2.3.4:5555', '172.16.1.2:5555', 'projector.local:5555'):
            with self.subTest(endpoint=endpoint):
                self.states = ['missing', 'device']
                self.commands.clear()
                pad = self.start(endpoint)
                self.assertEqual(self.commands[:4], [(endpoint, c) for c in (
                    ('get-state',), ('connect', endpoint), ('get-state',),
                    ('shell', 'getprop', 'ro.product.model'))])
                self.assertEqual(sum(c[0] == 'connect' for _, c in self.commands), 1)
                self.assertTrue(all(serial == endpoint for serial, _ in self.commands))
                pad.close()

    def test_missing_non_endpoint_or_nonprivate_target_never_connects(self):
        for endpoint in ('USB-serial', 'emulator-5554', '192.168.1.42', '192.168.1.42:0',
                         '192.168.1.42:65536', '192.168.1.42:abc', '127.0.0.1:5555',
                         '169.254.1.2:5555', '8.8.8.8:5555', '172.32.0.1:5555',
                         '[::1]:5555', 'projector.example.com:5555', '-bad.local:5555',
                         'bad local:5555', 'projector.local:5555;echo'):
            with self.subTest(endpoint=endpoint):
                self.states = ['missing']
                self.commands.clear()
                with self.assertRaises(ConnectionError):
                    pointer.start(endpoint)
                self.assertEqual(self.commands, [(endpoint, ('get-state',))])
                self.process.assert_not_called()

    def test_connect_result_requires_device_state_without_retry(self):
        for state in ('missing', 'offline', 'unauthorized'):
            with self.subTest(state=state):
                self.states = ['missing', state]
                self.commands.clear()
                with self.assertRaises(ConnectionError):
                    pointer.start('192.168.1.42:5555')
                self.assertEqual([c[0] for _, c in self.commands], ['get-state', 'connect', 'get-state'])
                self.process.assert_not_called()

    def test_wrong_model_after_recovery_never_starts_scrcpy(self):
        self.states = ['missing', 'device']
        self.model = 'Other TV'
        with self.assertRaises(ValueError):
            pointer.start('192.168.1.42:5555')
        self.assertEqual([c[0] for _, c in self.commands], ['get-state', 'connect', 'get-state', 'shell'])
        self.process.assert_not_called()

    def test_connect_timeout_does_not_retry_or_start_scrcpy(self):
        self.states = ['missing']
        self.connect_error = subprocess.TimeoutExpired('adb connect', 8)
        with self.assertRaises(subprocess.TimeoutExpired):
            pointer.start('192.168.1.42:5555')
        self.assertEqual([c[0] for _, c in self.commands], ['get-state', 'connect'])
        self.process.assert_not_called()


class StopTests(unittest.TestCase):
    def test_stop_interrupts_blocked_sdk_and_hardware_sequences(self):
        for hardware in (False, True):
            with self.subTest(hardware=hardware):
                entered, resume, fenced = (threading.Event() for _ in range(3))
                packets, errors = [], []

                class Wire:
                    def settimeout(self, _timeout):
                        pass
                    def sendall(self, packet):
                        packets.append(packet)
                        moving = (packet[0] == 13 and any(packet[6:8])) if hardware else packet[1] == 2
                        if moving and not entered.is_set():
                            entered.set()
                            if not resume.wait(2):
                                raise TimeoutError('Test did not unblock send')
                    def close(self):
                        pass

                pad = (pointer.HardwarePointer if hardware else pointer.Pointer)(Wire(), 1920, 1080)
                if hardware:
                    pad.press()

                def run():
                    try:
                        pad.move(256, 0) if hardware else pad.scroll(0, -1)
                    except BaseException as exc:
                        errors.append(exc)

                def stop():
                    try:
                        pad.request_stop()
                    except BaseException as exc:
                        errors.append(exc)
                    finally:
                        fenced.set()

                worker = threading.Thread(target=run)
                stopper = threading.Thread(target=stop)
                closer = threading.Thread(target=pad.close)
                worker.start()
                try:
                    self.assertTrue(entered.wait(1))
                    stopper.start()
                    self.assertTrue(fenced.wait(1), 'STOP waited for the input lock')
                    closer.start()
                finally:
                    resume.set()
                    for thread in (worker, stopper, closer):
                        if thread.ident is not None:
                            thread.join(2)
                            self.assertFalse(thread.is_alive())
                    pad.close()
                self.assertEqual(len(errors), 1)
                self.assertIsInstance(errors[0], ConnectionError)
                if hardware:
                    self.assertEqual([p[0] for p in packets], [12, 13, 13, 13, 14])
                    self.assertEqual(packets[-2], bytes.fromhex('0d000100050000000000'))
                    self.assertEqual(packets[-1], bytes.fromhex('0e0001'))
                else:
                    events = [struct.unpack('>BBQiiHHHII', p) for p in packets]
                    self.assertEqual([e[1] for e in events], [0, 2, 1])
                    self.assertEqual(events[-1][3:5], events[-2][3:5])
                self.assertFalse(pad.connected)

    def test_close_requests_stop_before_waiting_for_input_lock(self):
        for cls in (pointer.Pointer, pointer.HardwarePointer):
            with self.subTest(pointer=cls.__name__):
                sender, reader = socket.socketpair()
                self.addCleanup(sender.close)
                self.addCleanup(reader.close)
                pad = cls(sender, 1920, 1080)
                fenced = threading.Event()
                request_stop = pad.request_stop

                def fence():
                    request_stop()
                    fenced.set()

                closer = threading.Thread(target=pad.close)
                try:
                    with patch.object(pad, 'request_stop', side_effect=fence):
                        with pad._lock:
                            closer.start()
                            self.assertTrue(fenced.wait(1), 'close waited before fencing')
                            with self.assertRaises(ConnectionError):
                                pad.press()
                        closer.join(2)
                        self.assertFalse(closer.is_alive())
                finally:
                    pad.close()
                    reader.close()


if __name__ == '__main__':
    unittest.main()
