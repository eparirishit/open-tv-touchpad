"""Disposable, single-event-loop Android TV Remote v2 adapter for the H0 proof.

Private context/protocol fields are deliberately tied to androidtvremote2==0.3.2.
Re-review those hooks before upgrading. Pairing alone permits unverified TLS;
normal control uses OpenSSL verification plus an exact enrolled DER certificate.
"""
from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from importlib.metadata import version
import ipaddress
import os
from pathlib import Path
import re
import ssl
import tempfile

from androidtvremote2 import AndroidTVRemote
from androidtvremote2.certificate_generator import generate_selfsigned_cert
from androidtvremote2.const import LOGGER
from androidtvremote2.remotemessage_pb2 import RemoteDirection

# Upstream logs codes, certificates' identities and raw protocol at DEBUG/ERROR.
LOGGER.disabled = True
_IO_TIMEOUT = 12.0
_KEYCODES = {
    'UP': 19, 'DOWN': 20, 'LEFT': 21, 'RIGHT': 22, 'OK': 23,
    'HOME': 3, 'BACK': 4, 'VOLUME_UP': 24, 'VOLUME_DOWN': 25, 'PLAY_PAUSE': 85,
}
_PRIVATE_NETWORKS = tuple(ipaddress.IPv4Network(net) for net in
                          ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))


class _PinnedRemote(AndroidTVRemote):
    """Replace the pinned dependency's otherwise unverified TLS context."""

    enrolled: bytes | None = None
    pairing = False

    async def _create_ssl_context(self) -> ssl.SSLContext:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        # The TV's certificate names are not its IP; the enrolled cert is the anchor.
        context.check_hostname = False
        if self.pairing:
            context.verify_mode = ssl.CERT_NONE
        else:
            if not self.enrolled:
                raise ConnectionError('TV pairing required')
            context.verify_mode = ssl.CERT_REQUIRED
            context.load_verify_locations(cadata=ssl.DER_cert_to_PEM_cert(self.enrolled))
        context.load_cert_chain(self._certfile, self._keyfile)
        return context

    def peer_is_enrolled(self) -> bool:
        if not self.enrolled or not self._transport or self._transport.is_closing():
            return False
        peer = self._transport.get_extra_info('ssl_object')
        return bool(peer and peer.context.verify_mode == ssl.CERT_REQUIRED
                    and peer.getpeercert(binary_form=True) == self.enrolled)


class TVRemote:
    """Explicit pairing/reconnect only. Call all methods on the owning event loop.

    Input errors raise ValueError; transport/pairing failures raise sanitized
    ConnectionError. Cancelled operations propagate CancelledError after cleanup.
    A successful send means queued locally, not observed activation on the TV.
    """

    def __init__(self, host: str):
        try:
            address = ipaddress.IPv4Address(host) if isinstance(host, str) else None
        except ipaddress.AddressValueError:
            address = None
        if address is None or not any(address in network for network in _PRIVATE_NETWORKS):
            raise ValueError('TV target must be a private IPv4 address')
        if version('androidtvremote2') != '0.3.2':
            raise RuntimeError('TV adapter requires androidtvremote2 0.3.2')
        self._host = str(address)
        self._remote: _PinnedRemote | None = None
        self._credentials: tempfile.TemporaryDirectory | None = None
        self._candidate: bytes | None = None
        self._pending: asyncio.Task | None = None
        self._ready = False

    @property
    def connected(self) -> bool:
        remote = self._remote
        if not self._ready or not remote or not remote._remote_message_protocol:
            return False
        protocol = remote._remote_message_protocol
        return not protocol.on_con_lost.done() and remote.peer_is_enrolled()

    @asynccontextmanager
    async def _operation(self, failure: str):
        if self._pending is not None:
            raise ConnectionError('TV operation already in progress')
        self._pending = asyncio.current_task()
        try:
            async with asyncio.timeout(_IO_TIMEOUT):
                yield
        except asyncio.CancelledError:
            self.disconnect()
            raise
        except Exception:
            self.disconnect()
            raise ConnectionError(failure) from None
        finally:
            self._pending = None

    async def start_pairing(self) -> None:
        async with self._operation('TV pairing could not start'):
            self.close()
            # Only generation runs off-loop; cancellation cannot leave a writer
            # recreating credentials after close() deletes their directory.
            cert, key = await asyncio.to_thread(generate_selfsigned_cert, 'Local Hub Proof')
            self._credentials = tempfile.TemporaryDirectory(prefix='tv-hub-')
            directory = Path(self._credentials.name)
            for name, data in (('client.pem', cert), ('client.key', key)):
                with os.fdopen(os.open(directory / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                                       0o600), 'wb') as output:
                    output.write(data)
            self._remote = _PinnedRemote('Local Hub Proof', str(directory / 'client.pem'),
                                        str(directory / 'client.key'), self._host,
                                        enable_ime=False, enable_voice=False)
            self._remote.pairing = True
            await self._remote.async_start_pairing()
            # finish_pairing closes this transport; retain its actual certificate now.
            protocol = self._remote._pairing_message_protocol
            peer = protocol.transport.get_extra_info('ssl_object')
            self._candidate = peer.getpeercert(binary_form=True)
            if not self._candidate:
                raise ConnectionError('TV certificate unavailable')

    async def finish_pairing(self, code: str) -> None:
        if not isinstance(code, str) or re.fullmatch(r'[0-9A-Fa-f]{6}', code) is None:
            raise ValueError('Pairing code must contain six hexadecimal characters')
        async with self._operation('TV pairing or verified connection failed'):
            remote = self._remote
            if not remote or not self._candidate or not remote.pairing:
                raise ConnectionError('TV pairing required')
            candidate = self._candidate
            # The library verifies the TV code against this transport's RSA keys.
            await remote.async_finish_pairing(code)
            remote.enrolled = candidate
            remote.pairing = False
            self._candidate = None
            await self._connect()

    async def reconnect(self) -> None:
        async with self._operation('TV verified connection failed'):
            self.disconnect()
            await self._connect()

    async def _connect(self) -> None:
        remote = self._remote
        if not remote or not remote.enrolled or remote.pairing:
            raise ConnectionError('TV pairing required')
        await remote.async_connect()
        # Reject even a valid child of the enrolled certificate, or an upstream
        # context change, before exposing any control method.
        if not remote.peer_is_enrolled():
            raise ConnectionError('TV certificate verification failed')
        self._ready = True

    def _control_remote(self) -> _PinnedRemote:
        if not self.connected:
            self.disconnect()
            raise ConnectionError('TV is not connected')
        # No input queue/replay: a backed-up TLS writer loses this session.
        if self._remote._transport.get_write_buffer_size() > 4096:
            self.disconnect()
            raise ConnectionError('TV connection is backlogged')
        return self._remote

    def key(self, name: str) -> None:
        if not isinstance(name, str) or name not in _KEYCODES:
            raise ValueError('Unsupported TV key')
        remote = self._control_remote()
        try:
            remote.send_key_command(_KEYCODES[name], RemoteDirection.SHORT)
        except Exception:
            self.disconnect()
            raise ConnectionError('TV key could not be sent') from None

    def launch_netflix(self) -> None:
        remote = self._control_remote()
        try:
            # The projector rejects the package-launch link; this mobile-app VIEW URI resolves directly.
            remote.send_launch_app_command('nflx://www.netflix.com/browse')
        except Exception:
            self.disconnect()
            raise ConnectionError('Netflix command could not be sent') from None

    def disconnect(self) -> None:
        self._ready = False
        self._candidate = None
        try:
            current = asyncio.current_task()
        except RuntimeError:  # Tk callbacks also run between event-loop pump ticks.
            current = None
        if self._pending and self._pending is not current:
            self._pending.cancel()
        remote = self._remote
        if remote:
            # abort discards unsent input instead of flushing stale commands.
            if remote._transport:
                remote._transport.abort()
            pairing = remote._pairing_message_protocol
            if pairing and pairing.transport:
                pairing.transport.abort()
            remote.disconnect()
            remote.pairing = False

    def close(self) -> None:
        self.disconnect()
        self._remote = None
        if self._credentials:
            self._credentials.cleanup()
            self._credentials = None
