"""Local, disposable input client for the pinned scrcpy 5.0.1 server.

Wire format: upstream v5.0.1 ControlMessageReader / control_msg.c (Apache-2.0).
No video, clipboard, file-transfer, arbitrary shell or network-facing API.
"""
import hashlib
import ipaddress
import math
from pathlib import Path
import re
import secrets
import socket
import struct
import subprocess
import threading
import time

VERSION = '5.0.1'
SERVER_SHA256 = '764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536'
SERVER = Path('/opt/homebrew/share/scrcpy/scrcpy-server')


def adb(serial, *args, timeout=8):
    """Only internal fixed command callers; arguments never pass through a shell."""
    result = subprocess.run(
        ['adb', '-H', '127.0.0.1', '-P', '5037', '-s', serial, *args],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=timeout,
        check=False, text=True,
    )
    if result.returncode:
        raise ConnectionError('Projector command failed; check the ADB connection.')
    return result.stdout.strip()


def _ensure_transport(serial):
    try:
        if adb(serial, 'get-state') == 'device':
            return
    except ConnectionError:
        pass

    host, _, port = serial.rpartition(':')
    if not re.fullmatch(r'[0-9]{1,5}', port) or not 1 <= int(port) <= 65535:
        raise ConnectionError('ADB transport unavailable; reconnect the configured target.')
    try:
        address = ipaddress.IPv4Address(host)
        allowed = any(address in ipaddress.IPv4Network(network)
                      for network in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
    except ipaddress.AddressValueError:
        allowed = len(host) <= 253 and re.fullmatch(
            r'(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+local', host, re.IGNORECASE | re.ASCII)
    if not allowed:
        raise ConnectionError('ADB recovery requires a private IPv4 or .local endpoint with a port.')

    # Recover only the supplied endpoint; this does not repair DNS or restart adb.
    adb(serial, 'connect', serial, timeout=8)
    if adb(serial, 'get-state') != 'device':
        raise ConnectionError('Configured ADB target is not ready after reconnect.')


class Pointer:
    def __init__(self, connection, width, height):
        if not (1 <= width <= 65535 and 1 <= height <= 65535):
            raise ValueError('Unsupported display dimensions.')
        self.socket = connection
        self.socket.settimeout(.2)
        self.width, self.height = width, height
        self.x, self.y = width // 2, height // 2
        self.down = False
        self.pressed_at = self.last_input = 0.
        self._lock = threading.RLock()
        self._stopping = threading.Event()
        self._cleanup = None

    @property
    def connected(self):
        return self.socket is not None

    @staticmethod
    def _delta(value):
        if type(value) not in (int, float) or not math.isfinite(value) or abs(value) > 256:
            raise ValueError('Pointer delta is outside the proof bounds.')
        return round(value)

    def request_stop(self):
        self._stopping.set()

    def _event(self, action, *, cleanup=False):
        packet = struct.pack('>BBQiiHHHII', 2, action, 0xffffffffffffffff,
                             self.x, self.y, self.width, self.height,
                             65535 if action in (0, 2) else 0, 0, 0)
        self._send(packet, cleanup=cleanup)

    def _send(self, packet, *, cleanup=False):
        if self._stopping.is_set() and not cleanup:
            raise ConnectionError('Touchpad is stopping; input was discarded.')
        if self.socket is None:
            raise ConnectionError('Touchpad is disconnected.')
        try:
            self.socket.sendall(packet)
            self.last_input = time.monotonic()
        except OSError:
            self.socket.close()
            self.socket = None
            self.down = False
            raise ConnectionError('Touchpad connection lost; input was not replayed.') from None

    def move(self, dx, dy):
        dx, dy = self._delta(dx), self._delta(dy)
        with self._lock:
            previous = self.x, self.y
            self.x = max(0, min(self.width - 1, self.x + dx))
            self.y = max(0, min(self.height - 1, self.y + dy))
            try:
                self._event(2 if self.down else 7)
            except ConnectionError:
                self.x, self.y = previous
                raise

    def press(self):
        with self._lock:
            if not self.down:
                self._event(0)
                self.down = True
                self.pressed_at = time.monotonic()

    def expire(self):
        with self._lock:
            now = time.monotonic()
            if self.down and (now - self.last_input >= 2 or now - self.pressed_at >= 15):
                self.close()

    def release(self):
        with self._lock:
            if self.down:
                self._event(1)
                self.down = False
                self._event(7)

    def tap(self):
        with self._lock:
            self.press()
            time.sleep(.08)
            self.release()

    def scroll(self, horizontal, vertical):
        if horizontal not in (-1, 0, 1) or vertical not in (-1, 0, 1):
            raise ValueError('Scroll direction is invalid.')
        if horizontal == vertical == 0:
            return
        with self._lock:
            if self.down:
                return
            start = self.x, self.y
            target = (max(0, min(self.width - 1, self.x + horizontal * 160)),
                      max(0, min(self.height - 1, self.y + vertical * 160)))
            self.press()
            try:
                for step in range(1, 8):
                    previous = self.x, self.y
                    self.x = round(start[0] + (target[0] - start[0]) * step / 7)
                    self.y = round(start[1] + (target[1] - start[1]) * step / 7)
                    try:
                        self._event(2)
                    except ConnectionError:
                        self.x, self.y = previous
                        raise
                    time.sleep(.016)
                self._event(1)
                self.down = False
                self.x, self.y = start
                self._event(7)
            finally:
                if self.down:
                    self.close()

    def close(self):
        self.request_stop()
        with self._lock:
            if self.socket is not None:
                try:
                    if self.down:
                        self._event(1, cleanup=True)
                except ConnectionError:
                    pass
                if self.socket is not None:
                    self.socket.close()
                self.socket = None
            self.down = False
            if self._cleanup:
                cleanup, self._cleanup = self._cleanup, None
                cleanup()


class HardwarePointer(Pointer):
    # Descriptor adapted from scrcpy v5.0.1 app/src/hid/hid_mouse.c.
    # Copyright Genymobile / Romain Vimont; see scrcpy-LICENSE. Python framing added here.
    DESCRIPTOR = bytes.fromhex(
        '05 01 09 02 A1 01 09 01 A1 00 '
        '05 09 19 01 29 05 15 00 25 01 95 05 75 01 81 02 '
        '95 01 75 03 81 01 '
        '05 01 09 30 09 31 09 38 15 81 25 7F 75 08 95 03 81 06 '
        '05 0C 0A 38 02 15 81 25 7F 75 08 95 01 81 06 C0 C0')

    def __init__(self, connection, width, height):
        super().__init__(connection, width, height)
        name = b'Open TV Touchpad'
        self._send(struct.pack('>BHHHB', 12, 1, 0, 0, len(name)) + name
                   + struct.pack('>H', len(self.DESCRIPTOR)) + self.DESCRIPTOR)

    def _report(self, buttons, dx=0, dy=0, wheel=0, pan=0, *, cleanup=False):
        self._send(struct.pack('>BHHBbbbb', 13, 1, 5, buttons, dx, dy, wheel, pan), cleanup=cleanup)

    def _event(self, action, *, cleanup=False):
        self._report(1 if action in (0, 2) else 0, cleanup=cleanup)

    def move(self, dx, dy):
        dx, dy = self._delta(dx), self._delta(dy)
        with self._lock:
            while True:
                x, y = max(-127, min(127, dx)), max(-127, min(127, dy))
                self._report(int(self.down), x, y)
                dx, dy = dx - x, dy - y
                if dx == dy == 0:
                    break

    def scroll(self, horizontal, vertical):
        if horizontal not in (-1, 0, 1) or vertical not in (-1, 0, 1):
            raise ValueError('Scroll direction is invalid.')
        with self._lock:
            if not self.down:
                self._report(0, wheel=vertical, pan=-horizontal)

    def close(self):
        self.request_stop()
        with self._lock:
            if self.socket is not None:
                try:
                    if self.down:
                        self._event(1, cleanup=True)
                        self.down = False
                    self._send(struct.pack('>BH', 14, 1), cleanup=True)
                except ConnectionError:
                    pass
            super().close()


def start(serial, *, hardware=True):
    """Start exactly one owned no-video server and loopback tunnel."""
    if hashlib.sha256(SERVER.read_bytes()).hexdigest() != SERVER_SHA256:
        raise ValueError('Installed scrcpy server does not match the tested 5.0.1 binary.')
    _ensure_transport(serial)
    if adb(serial, 'shell', 'getprop', 'ro.product.model') != 'XK03H':
        raise ValueError('This hardware proof is restricted to the XGIMI XK03H.')
    dimensions = re.findall(r'(?:Physical|Override) size: (\d+)x(\d+)',
                            adb(serial, 'shell', 'wm', 'size'))
    if not dimensions:
        raise ValueError('Cannot determine projector display dimensions.')
    width, height = map(int, dimensions[-1])
    scid = f'{secrets.randbits(31):08x}'
    path = f'/data/local/tmp/ott-h0-{scid}.jar'
    port = None
    process = None
    connection = None

    def cleanup():
        if process is not None:
            try:
                process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=1)
        if port is not None:
            try:
                adb(serial, 'forward', '--remove', f'tcp:{port}', timeout=3)
            except (OSError, ConnectionError, subprocess.TimeoutExpired):
                pass
        try:
            # Remove only the random file this invocation owns if startup failed.
            adb(serial, 'shell', 'rm', '-f', path, timeout=3)
        except (OSError, ConnectionError, subprocess.TimeoutExpired):
            pass

    try:
        adb(serial, 'push', str(SERVER), path)
        port_text = adb(serial, 'forward', 'tcp:0', f'localabstract:scrcpy_{scid}')
        if not port_text.isdecimal() or not 1024 <= int(port_text) <= 65535:
            raise ConnectionError('Could not allocate a local control tunnel.')
        port = int(port_text)
        process = subprocess.Popen(
            ['adb', '-H', '127.0.0.1', '-P', '5037', '-s', serial, 'shell',
             f'CLASSPATH={path}', 'app_process', '/', 'com.genymobile.scrcpy.Server',
             VERSION, f'scid={scid}', 'tunnel_forward=true', 'video=false',
             'audio=false', 'control=true', 'display_id=0', 'clipboard_autosync=false',
             'power_on=false', 'cleanup=true', 'send_dummy_byte=true',
             'send_device_meta=false', 'log_level=error'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise ConnectionError('The control-only server stopped during startup.')
            try:
                connection = socket.create_connection(('127.0.0.1', port), timeout=.3)
                if connection.recv(1) == b'\0':
                    break
            except OSError:
                pass
            if connection is not None:
                connection.close()
                connection = None
            time.sleep(.1)
        else:
            raise ConnectionError('The control-only server did not become ready.')
        pad = (HardwarePointer if hardware else Pointer)(connection, width, height)
        pad._cleanup = cleanup

        def drain():
            # No clipboard enabled; discard bounded server messages and detect EOF.
            while pad.connected:
                try:
                    if not connection.recv(4096):
                        break
                except TimeoutError:
                    pad.expire()
                    continue
                except OSError:
                    break
            pad.close()

        threading.Thread(target=drain, daemon=True, name='scrcpy-status').start()
        return pad
    except BaseException:
        if connection is not None:
            connection.close()
        cleanup()
        raise
