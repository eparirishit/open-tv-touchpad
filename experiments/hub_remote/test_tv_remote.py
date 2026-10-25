"""Local TLS regression proof; socket pairs never listen or contact a TV."""
import asyncio
import hashlib
import logging
from pathlib import Path
import socket
import ssl as tls
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from androidtvremote2.base import ProtobufProtocol
from androidtvremote2.certificate_generator import generate_selfsigned_cert
from androidtvremote2.polo_pb2 import OuterMessage
from androidtvremote2.remotemessage_pb2 import RemoteMessage

import tv_remote


class FixtureProtocol(ProtobufProtocol):
    def __init__(self, fixture, pairing):
        super().__init__(asyncio.get_running_loop().create_future())
        self.fixture = fixture
        self.pairing = pairing

    def connection_made(self, transport):
        super().connection_made(transport)
        self.fixture.protocols.append(self)
        peer = transport.get_extra_info('ssl_object').getpeercert(True)
        self.fixture.client_identities.append(peer)
        if self.pairing:
            client = x509.load_der_x509_certificate(peer).public_key().public_numbers()
            server = x509.load_pem_x509_certificate(self.fixture.pair_cert).public_key().public_numbers()
            # Independent fixture of the v2 code's RSA public-key binding.
            fields = [client.n, client.e, server.n, server.e]
            encoded = b''.join(n.to_bytes((n.bit_length() + 7) // 8, 'big') for n in fields)
            self.secret = hashlib.sha256(encoded + bytes.fromhex('ABCD')).digest()
            self.fixture.code = self.secret[:1].hex().upper() + 'ABCD'
        elif not self.fixture.stall_connect:
            message = RemoteMessage()
            message.remote_start.started = True
            self._send_message(message)

    def _handle_message(self, raw):
        if not self.pairing:
            self.fixture.commands.put_nowait(RemoteMessage.FromString(raw))
            return
        message = OuterMessage.FromString(raw)
        reply = OuterMessage(protocol_version=2, status=OuterMessage.STATUS_OK)
        if message.HasField('pairing_request'):
            if self.fixture.stall_pairing:
                return
            reply.pairing_request_ack.SetInParent()
        elif message.HasField('options'):
            reply.options.SetInParent()
        elif message.HasField('configuration'):
            reply.configuration_ack.SetInParent()
        elif message.HasField('secret'):
            if message.secret.secret != self.secret:
                self.transport.abort()
                return
            reply.secret_ack.secret = self.secret
        self._send_message(reply)


class TVRemoteTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.pair_cert, self.pair_key = await asyncio.to_thread(generate_selfsigned_cert, 'fixture-tv')
        self.control_cert, self.control_key = self.pair_cert, self.pair_key
        self.protocols = []
        self.server_tasks = []
        self.client_identities = []
        self.commands = asyncio.Queue()
        self.stall_connect = False
        self.stall_pairing = False
        self.code = None
        self.remote = tv_remote.TVRemote('10.1.2.3')
        self.loop = asyncio.get_running_loop()
        self.original_connect = self.loop.create_connection
        self.loop.create_connection = self.connect_fixture
        self.previous_handler = self.loop.get_exception_handler()
        self.loop_errors = []
        self.loop.set_exception_handler(lambda _loop, context: self.loop_errors.append(context))

    async def asyncTearDown(self):
        self.remote.close()
        self.loop.create_connection = self.original_connect
        for protocol in self.protocols:
            protocol.transport.abort()
        for task in self.server_tasks:
            task.cancel()
        await asyncio.gather(*self.server_tasks, return_exceptions=True)
        await asyncio.sleep(0)
        self.loop.set_exception_handler(self.previous_handler)
        unexpected = [type(context.get('exception')).__name__ for context in self.loop_errors
                      if not isinstance(context.get('exception'), (tls.SSLError, ConnectionResetError))]
        self.assertEqual(unexpected, [])

    async def connect_fixture(self, factory, host, port, *, ssl):
        self.assertEqual(host, '10.1.2.3')
        self.assertIn(port, (6466, 6467))
        pairing = port == 6467
        cert, key = (self.pair_cert, self.pair_key) if pairing else (self.control_cert, self.control_key)
        certfile = Path(self.temp.name) / 'server.pem'
        keyfile = Path(self.temp.name) / 'server.key'
        certfile.write_bytes(cert)
        keyfile.write_bytes(key)
        context = tls.SSLContext(tls.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile, keyfile)
        context.verify_mode = tls.CERT_REQUIRED
        context.load_verify_locations(self.remote._remote._certfile)
        client_socket, server_socket = socket.socketpair()
        client_socket.setblocking(False)
        server_socket.setblocking(False)
        server_task = asyncio.create_task(self.loop.connect_accepted_socket(
            lambda: FixtureProtocol(self, pairing), server_socket, ssl=context))
        self.server_tasks.append(server_task)
        result = None
        try:
            result = await self.original_connect(factory, sock=client_socket, ssl=ssl,
                                                 server_hostname='fixture-address')
            await server_task
            return result
        except BaseException:
            if result:
                result[0].abort()
            server_task.cancel()
            client_socket.close()
            server_socket.close()
            raise

    async def pair(self):
        await self.remote.start_pairing()
        self.assertFalse(self.remote.connected)
        await self.remote.finish_pairing(self.code)
        self.assertTrue(self.remote.connected)

    async def command(self):
        return await asyncio.wait_for(self.commands.get(), 1)

    async def test_private_ipv4_only(self):
        for host in ('example.com', '8.8.8.8', '127.0.0.1', '0.0.0.0', '169.254.1.1',
                     '100.64.0.1', '192.0.2.1', '224.0.0.1', '::1', '192.168.1.2:6466', 123):
            with self.subTest(host=host), self.assertRaises(ValueError):
                tv_remote.TVRemote(host)
        for host in ('10.0.0.1', '172.16.0.1', '192.168.1.1'):
            tv_remote.TVRemote(host).close()

    async def test_unpaired_identity_cannot_send_or_reconnect(self):
        with self.assertRaises(ConnectionError):
            await self.remote.reconnect()
        with self.assertRaises(ConnectionError):
            self.remote.key('UP')
        with self.assertRaises(ConnectionError):
            self.remote.launch_netflix()
        self.assertEqual(self.client_identities, [])

    async def test_pair_controls_short_allowlist_and_fixed_netflix(self):
        await self.pair()
        for name, code in [('UP', 19), ('DOWN', 20), ('LEFT', 21), ('RIGHT', 22),
                           ('OK', 23), ('HOME', 3), ('BACK', 4), ('VOLUME_UP', 24),
                           ('VOLUME_DOWN', 25), ('PLAY_PAUSE', 85)]:
            self.remote.key(name)
            msg = await self.command()
            self.assertEqual((msg.remote_key_inject.key_code, msg.remote_key_inject.direction),
                             (code, 3))
        for name in ('POWER', 'text:secret', 'KEYCODE_HOME', 'up', '', 26, None):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.remote.key(name)
        self.remote.launch_netflix()
        msg = await self.command()
        self.assertEqual(msg.remote_app_link_launch_request.app_link,
                         'nflx://www.netflix.com/browse')
        self.assertTrue(self.commands.empty())

    async def test_disconnect_reconnect_reuses_identity_and_close_deletes_it(self):
        await self.pair()
        certificate = Path(self.remote._remote._certfile)
        key = Path(self.remote._remote._keyfile)
        self.assertEqual(certificate.parent.stat().st_mode & 0o777, 0o700)
        self.assertEqual(key.stat().st_mode & 0o777, 0o600)
        self.assertEqual(certificate.stat().st_mode & 0o777, 0o600)
        self.remote.disconnect()
        self.assertFalse(self.remote.connected)
        self.assertTrue(key.exists())
        await self.remote.reconnect()
        self.assertTrue(self.remote.connected)
        self.assertEqual(len(set(self.client_identities)), 1)
        self.assertTrue(self.commands.empty())
        self.remote.close()
        self.assertFalse(certificate.parent.exists())
        with self.assertRaises(ConnectionError):
            await self.remote.reconnect()

    async def test_unexpected_certificate_rejected_by_tls(self):
        await self.pair()
        self.remote.disconnect()
        self.control_cert, self.control_key = await asyncio.to_thread(generate_selfsigned_cert, 'unexpected-tv')
        with self.assertRaises(ConnectionError):
            await self.remote.reconnect()
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.commands.empty())
        self.assertEqual(len(self.client_identities), 2)  # rejected before server protocol starts
        with self.assertRaises(ConnectionError):
            self.remote.key('UP')

    async def test_trusted_child_certificate_is_not_the_enrolled_certificate(self):
        await self.pair()
        self.remote.disconnect()
        enrolled = x509.load_pem_x509_certificate(self.pair_cert)
        issuer_key = serialization.load_pem_private_key(self.pair_key, None)
        child_key = await asyncio.to_thread(rsa.generate_private_key, public_exponent=65537, key_size=2048)
        now = datetime.now(timezone.utc)
        child = (x509.CertificateBuilder()
                 .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'child')]))
                 .issuer_name(enrolled.subject).public_key(child_key.public_key())
                 .serial_number(2001).not_valid_before(now - timedelta(minutes=1))
                 .not_valid_after(now + timedelta(days=1)).sign(issuer_key, hashes.SHA256()))
        self.control_cert = child.public_bytes(serialization.Encoding.PEM)
        self.control_key = child_key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.TraditionalOpenSSL, serialization.NoEncryption())
        with self.assertRaises(ConnectionError):
            await self.remote.reconnect()
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.commands.empty())

    async def test_pairing_code_failure_does_not_enroll_or_log_secrets(self):
        records = []
        class Capture(logging.Handler):
            def emit(self, record):
                records.append(record.getMessage())
        logger = logging.getLogger('androidtvremote2')
        handler = Capture()
        old_level = logger.level
        logger.addHandler(handler)
        logger.setLevel(logging.DEBUG)
        try:
            await self.remote.start_pairing()
            wrong = ('00' if self.code[:2] != '00' else 'FF') + self.code[2:]
            with self.assertRaises(ConnectionError) as raised:
                await self.remote.finish_pairing(wrong)
            self.assertNotIn(wrong, str(raised.exception))
            self.assertTrue(raised.exception.__suppress_context__)
            self.assertEqual(records, [])
            with self.assertRaises(ConnectionError):
                await self.remote.reconnect()
        finally:
            logger.removeHandler(handler)
            logger.setLevel(old_level)

    async def test_malformed_pairing_code_is_rejected(self):
        await self.remote.start_pairing()
        for code in ('12345', '1234567', ' 12345', 'ZZZZZZ', None):
            with self.subTest(code=code), self.assertRaises(ValueError):
                await self.remote.finish_pairing(code)
        self.assertFalse(self.remote.connected)

    async def test_peer_loss_immediately_blocks_controls_without_retry(self):
        await self.pair()
        client_protocol = self.remote._remote._remote_message_protocol
        self.protocols[-1].transport.abort()
        await asyncio.wait_for(client_protocol.on_con_lost, 1)
        self.assertFalse(self.remote.connected)
        with self.assertRaises(ConnectionError):
            self.remote.key('OK')
        await asyncio.sleep(0.03)
        self.assertEqual(len(self.client_identities), 2)

    async def test_connect_and_pairing_deadlines_abort_transport(self):
        await self.pair()
        self.remote.disconnect()
        original_timeout = tv_remote._IO_TIMEOUT
        tv_remote._IO_TIMEOUT = .05
        try:
            self.stall_connect = True
            with self.assertRaises(ConnectionError):
                await asyncio.wait_for(self.remote.reconnect(), 1)
            self.assertFalse(self.remote.connected)
            self.stall_pairing = True
            with self.assertRaises(ConnectionError):
                await asyncio.wait_for(self.remote.start_pairing(), 1)
            self.assertFalse(self.remote.connected)
            await asyncio.sleep(0.02)
            self.assertTrue(all(p.transport.is_closing() for p in self.protocols))
        finally:
            tv_remote._IO_TIMEOUT = original_timeout

    async def test_disconnect_cancels_inflight_connect(self):
        await self.pair()
        self.remote.disconnect()
        self.stall_connect = True
        connecting = asyncio.create_task(self.remote.reconnect())
        async with asyncio.timeout(1):
            while len(self.client_identities) < 3:
                await asyncio.sleep(0)
        self.remote.disconnect()
        with self.assertRaises(asyncio.CancelledError):
            await connecting
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.remote._remote._transport.is_closing())


    async def test_external_cancellation_still_propagates(self):
        await self.pair()
        self.remote.disconnect()
        self.stall_connect = True
        connecting = asyncio.create_task(self.remote.reconnect())
        async with asyncio.timeout(1):
            while len(self.client_identities) < 3:
                await asyncio.sleep(0)
        connecting.cancel()
        with self.assertRaises(asyncio.CancelledError):
            await connecting
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.remote._remote._transport.is_closing())

    async def test_runtime_certificate_must_match_pairing_transport(self):
        await self.remote.start_pairing()
        self.control_cert, self.control_key = await asyncio.to_thread(generate_selfsigned_cert, 'other-tv')
        with self.assertRaises(ConnectionError):
            await self.remote.finish_pairing(self.code)
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.commands.empty())
        self.assertEqual(len(self.client_identities), 1)

    async def test_expired_enrolled_certificate_fails_closed(self):
        original = x509.load_pem_x509_certificate(self.pair_cert)
        issuer_key = serialization.load_pem_private_key(self.pair_key, None)
        now = datetime.now(timezone.utc)
        expired = (x509.CertificateBuilder().subject_name(original.subject)
                   .issuer_name(original.issuer).public_key(issuer_key.public_key())
                   .serial_number(2002).not_valid_before(now - timedelta(days=2))
                   .not_valid_after(now - timedelta(days=1)).sign(issuer_key, hashes.SHA256()))
        self.pair_cert = expired.public_bytes(serialization.Encoding.PEM)
        self.control_cert = self.pair_cert
        await self.remote.start_pairing()
        with self.assertRaises(ConnectionError):
            await self.remote.finish_pairing(self.code)
        self.assertFalse(self.remote.connected)
        self.assertTrue(self.commands.empty())

    async def test_overlapping_operations_are_rejected(self):
        await self.pair()
        self.remote.disconnect()
        self.stall_connect = True
        connecting = asyncio.create_task(self.remote.reconnect())
        async with asyncio.timeout(1):
            while len(self.client_identities) < 3:
                await asyncio.sleep(0)
        with self.assertRaises(ConnectionError):
            await self.remote.start_pairing()
        self.remote.disconnect()
        with self.assertRaises(asyncio.CancelledError):
            await connecting
        self.assertEqual(len(self.client_identities), 3)



class TVRemoteSyncStopTests(unittest.TestCase):
    def test_stop_between_loop_ticks_cancels_and_close_removes_identity(self):
        loop = asyncio.new_event_loop()
        fixture = TVRemoteTests('test_disconnect_cancels_inflight_connect')
        async def prepare():
            await fixture.asyncSetUp()
            await fixture.pair()
            fixture.remote.disconnect()
            fixture.stall_connect = True
            task = asyncio.create_task(fixture.remote.reconnect())
            async with asyncio.timeout(1):
                while len(fixture.client_identities) < 3:
                    await asyncio.sleep(0)
            return task
        try:
            connecting = loop.run_until_complete(prepare())
            directory = Path(fixture.remote._remote._certfile).parent
            # A Tk callback runs on this thread without a running asyncio loop.
            fixture.remote.disconnect()
            self.assertFalse(fixture.remote.connected)
            with self.assertRaises(asyncio.CancelledError):
                loop.run_until_complete(connecting)
            self.assertTrue(directory.exists())
            fixture.remote.close()
            self.assertFalse(directory.exists())
        finally:
            loop.run_until_complete(fixture.asyncTearDown())
            fixture.temp.cleanup()
            loop.run_until_complete(loop.shutdown_default_executor())
            loop.close()
        fixture.remote.close()

    def test_close_between_loop_ticks_cancels_pending_pairing(self):
        loop = asyncio.new_event_loop()
        fixture = TVRemoteTests('test_malformed_pairing_code_is_rejected')
        async def prepare():
            await fixture.asyncSetUp()
            fixture.stall_pairing = True
            task = asyncio.create_task(fixture.remote.start_pairing())
            async with asyncio.timeout(1):
                while not fixture.client_identities:
                    await asyncio.sleep(0)
            return task
        try:
            pairing = loop.run_until_complete(prepare())
            directory = Path(fixture.remote._remote._certfile).parent
            fixture.remote.close()
            self.assertFalse(directory.exists())
            with self.assertRaises(asyncio.CancelledError):
                loop.run_until_complete(pairing)
            self.assertFalse(fixture.remote.connected)
        finally:
            loop.run_until_complete(fixture.asyncTearDown())
            fixture.temp.cleanup()
            loop.run_until_complete(loop.shutdown_default_executor())
            loop.close()


if __name__ == '__main__':
    unittest.main()
