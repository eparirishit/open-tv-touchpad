import asyncio
import json
import ssl
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from aiohttp import ClientSession, CookieJar, WSMsgType
from aiohttp.test_utils import TestServer
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

from hub.server import create_app, decode_json, BoundedSite


class SessionsFixture:
    """Explicit auth seam; persistence and approval are covered by session tests."""
    def authenticate(self, token):
        if token != 'valid':
            raise ValueError('invalid')
        return 'controller'

    def csrf(self, session):
        return 'csrf-value'

    def list_sessions(self):
        return [{'id': 'controller', 'label': 'Phone', 'expires': 9999999999}]

    def expire(self):
        return []

    def revoke(self, session):
        pass


def contexts(directory):
    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'localhost')])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now-timedelta(days=1)).not_valid_after(now+timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost')]), False)
            .sign(key, hashes.SHA256()))
    cp, kp = directory/'cert.pem', directory/'key.pem'
    cp.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    kp.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                    serialization.NoEncryption()))
    kp.chmod(0o600)
    server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    server.load_cert_chain(cp, kp)
    client = ssl.create_default_context(cafile=str(cp))
    return server, client


class JSONTests(unittest.TestCase):
    def test_rejects_duplicate_unknown_shape_and_nonfinite(self):
        for raw in ['{"kind":1,"kind":2}', '{"x":NaN}', '[]', 'null']:
            with self.assertRaises(ValueError):
                decode_json(raw)
        self.assertEqual(decode_json('{"label":"Phone"}'), {'label': 'Phone'})


class ServerTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tls, self.client_tls = contexts(Path(self.tmp.name))
        self.origin = 'https://192.168.1.2:8443'
        self.app = create_app(SessionsFixture(), None, origin=self.origin)
        self.server = TestServer(self.app, host='localhost', scheme='https')
        await self.server.start_server(ssl=self.tls)
        self.client = ClientSession(cookie_jar=CookieJar())
        self.headers = {'Host': '192.168.1.2:8443', 'Origin': self.origin}

    async def asyncTearDown(self):
        await self.client.close()
        await self.server.close()
        self.tmp.cleanup()

    async def request(self, method, path, **kw):
        headers = dict(self.headers)
        headers.update(kw.pop('headers', {}))
        return await self.client.request(method, self.server.make_url(path), headers=headers,
                                         ssl=self.client_tls, **kw)

    async def test_host_origin_and_no_auth(self):
        for hdr in [{'Host':'attacker.test'}, {'Origin':'null'}, {'Origin':'https://evil.test'}]:
            async with await self.request('POST','/api/enroll/request', headers=hdr, json={'label':'p'}) as r:
                self.assertEqual(r.status,403)
        async with self.client.post(self.server.make_url('/api/enroll/request'),
                                    ssl=self.client_tls, headers={'Host':'192.168.1.2:8443'},json={}) as r:
            self.assertEqual(r.status,403)
        async with await self.request('GET','/api/session') as r:
            self.assertEqual(r.status,401)
            self.assertEqual(r.headers['Cache-Control'],'no-store')
            self.assertNotIn('Access-Control-Allow-Origin',r.headers)

    async def test_static_allowlist_and_body_bounds(self):
        async with await self.request('GET','/server.py') as r:
            self.assertEqual(r.status,404)
        async with await self.request('POST','/api/enroll/request', data='a'*4097,
                                      headers={'Content-Type':'application/json'}) as r:
            self.assertEqual(r.status,413)

    async def test_authenticated_session_and_ws_hello(self):
        async with await self.request('GET','/api/session',headers={'Cookie':'__Host-ott_session=valid'}) as r:
            self.assertEqual(r.status,200)
            self.assertEqual((await r.json())['csrf'],'csrf-value')
        headers={**self.headers,'Cookie':'__Host-ott_session=valid'}
        async with self.client.ws_connect(self.server.make_url('/ws'),headers=headers,ssl=self.client_tls) as ws:
            await ws.send_json({'kind':'hello','version':1,'csrf':'csrf-value'})
            hello=await ws.receive_json()
            self.assertFalse(hello['status']['available'])
            await ws.send_json({'version':1,'seq':1,'kind':'connect','lease':'anything'})
            ack=await ws.receive_json()
            self.assertEqual(ack.get('kind'),'ack')
            self.assertEqual(ack['status'],'failed')
            self.assertEqual(ack['error'],'unavailable')

    async def test_wrong_csrf_and_binary_close(self):
        headers={**self.headers,'Cookie':'__Host-ott_session=valid'}
        for message in [{'kind':'hello','version':1,'csrf':'wrong'}, b'invalid']:
            async with self.client.ws_connect(self.server.make_url('/ws'),headers=headers,ssl=self.client_tls) as ws:
                if isinstance(message,bytes): await ws.send_bytes(message)
                else: await ws.send_json(message)
                reply=await ws.receive()
                self.assertIn(reply.type,(WSMsgType.CLOSE,WSMsgType.CLOSED))

    async def test_logout_requires_csrf(self):
        async with await self.request('POST','/api/logout',headers={'Cookie':'__Host-ott_session=valid'},json={}) as r:
            self.assertEqual(r.status,403)
        async with await self.request('POST','/api/logout',headers={'Cookie':'__Host-ott_session=valid','X-CSRF-Token':'csrf-value'},json={}) as r:
            self.assertEqual(r.status,200)
            self.assertIn('Max-Age=0',r.headers['Set-Cookie'])


class BudgetTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        import socket
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.tls, self.client_tls = contexts(Path(self.tmp.name))
        self.app = create_app(SessionsFixture(), None, origin='https://192.168.1.2:8443')
        sock = socket.socket()
        sock.bind(('127.0.0.1',0))
        self.port = sock.getsockname()[1]
        self.site = BoundedSite(self.app, sock, self.tls)
        await self.site.start()
        self.addAsyncCleanup(self.site.close)
        self.clients = []
        self.addAsyncCleanup(self.close_clients)

    async def close_clients(self):
        for _, writer in self.clients:
            writer.close()
        for _, writer in self.clients:
            with __import__('contextlib').suppress(OSError):
                await writer.wait_closed()

    async def test_32_budget_counts_tls_handshakes_and_rejects_33rd(self):
        for _ in range(32):
            self.clients.append(await asyncio.open_connection('127.0.0.1', self.port))
        reader, writer = await asyncio.open_connection('127.0.0.1', self.port)
        self.clients.append((reader, writer))
        self.assertEqual(await asyncio.wait_for(reader.read(1),1),b'')
        self.assertEqual(len(self.site.sockets),32)
        await asyncio.sleep(5.1)
        self.assertEqual(len(self.site.sockets),0)

    async def test_incomplete_https_headers_expire_and_valid_request_succeeds(self):
        reader, writer = await asyncio.open_connection('127.0.0.1', self.port,
                                                       ssl=self.client_tls,server_hostname='localhost')
        self.clients.append((reader, writer))
        writer.write(b'GET /api/session HTTP/1.1\r\nHost: ')
        await writer.drain()
        self.assertEqual(await asyncio.wait_for(reader.read(1),6),b'')
        async with ClientSession() as client:
            async with client.get(f'https://localhost:{self.port}/api/session',ssl=self.client_tls,
                                  headers={'Host':'192.168.1.2:8443','Cookie':'__Host-ott_session=valid'}) as r:
                self.assertEqual(r.status,200)
                self.assertEqual(r.headers['Connection'],'close')


class EnrollmentTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        from hub.sessions import Sessions
        from hub.controller import Controller
        from hub.server import STATE
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        directory=Path(self.tmp.name).resolve()
        self.tls,self.client_tls=contexts(directory)
        self.now=[1000.]
        self.sessions=Sessions(directory/'private'/'sessions.sqlite',clock=lambda:self.now[0])
        self.addCleanup(self.sessions.close)
        self.sessions.open_window()
        self.app=create_app(self.sessions,Controller(),origin='https://192.168.1.2:8443')
        self.server=TestServer(self.app,host='localhost',scheme='https')
        await self.server.start_server(ssl=self.tls)
        self.addAsyncCleanup(self.server.close)
        self.client=ClientSession(headers={'Host':'192.168.1.2:8443','Origin':'https://192.168.1.2:8443'})
        self.addAsyncCleanup(self.client.close)

    async def enroll(self):
        async with self.client.post(self.server.make_url('/api/enroll/request'),ssl=self.client_tls,json={'label':'Phone'}) as r:
            self.assertEqual(r.status,200)
            pending=await r.json()
            cookie=r.headers['Set-Cookie']
            for required in ('Secure','HttpOnly','SameSite=Strict','Path=/'):
                self.assertIn(required,cookie)
            self.assertNotIn('Domain=',cookie)
        async with self.client.post(self.server.make_url('/api/enroll/complete'),ssl=self.client_tls,
                json={},headers={'X-CSRF-Token':pending['csrf']}) as r:
            self.assertEqual(r.status,202)
        self.sessions.approve(self.sessions.list_pending()[0]['pending_id'])
        async with self.client.post(self.server.make_url('/api/enroll/complete'),ssl=self.client_tls,
                json={},headers={'X-CSRF-Token':pending['csrf']}) as r:
            self.assertEqual(r.status,200)
        async with self.client.get(self.server.make_url('/api/session'),ssl=self.client_tls) as r:
            self.assertEqual(r.status,200)
            return await r.json()

    async def test_owner_enrollment_lease_unavailable_and_expiry(self):
        data=await self.enroll()
        async with self.client.ws_connect(self.server.make_url('/ws'),ssl=self.client_tls) as ws:
            await ws.send_json({'kind':'hello','version':1,'csrf':data['csrf']})
            self.assertEqual((await ws.receive_json())['kind'],'hello')
            await ws.send_json({'version':1,'seq':1,'kind':'take_control'})
            lease=(await ws.receive_json())['lease']
            await ws.send_json({'version':1,'seq':2,'kind':'connect','lease':lease})
            self.assertEqual((await ws.receive_json())['error'],'unavailable')
            self.now[0]+=30*86400
            reply=await asyncio.wait_for(ws.receive(),1)
            self.assertIn(reply.type,(WSMsgType.CLOSE,WSMsgType.CLOSED))

    async def test_cookie_session_does_not_allow_cross_origin_ws(self):
        from aiohttp import WSServerHandshakeError
        await self.enroll()
        with self.assertRaises(WSServerHandshakeError) as caught:
            await self.client.ws_connect(self.server.make_url('/ws'),ssl=self.client_tls,headers={'Origin':'https://evil.test'})
        self.assertEqual(caught.exception.status,403)

    async def test_eight_socket_cap_and_ninth_rejected(self):
        from aiohttp import WSServerHandshakeError
        data=await self.enroll()
        sockets=[]
        try:
            for _ in range(8):
                ws=await self.client.ws_connect(self.server.make_url('/ws'),ssl=self.client_tls)
                sockets.append(ws)
                await ws.send_json({'kind':'hello','version':1,'csrf':data['csrf']})
                await ws.receive_json()
            with self.assertRaises(WSServerHandshakeError) as caught:
                await self.client.ws_connect(self.server.make_url('/ws'),ssl=self.client_tls)
            self.assertEqual(caught.exception.status,503)
        finally:
            await asyncio.gather(*(ws.close() for ws in sockets))


class MemorySocket:
    """No network: exercise handlers with a stalled close/drain boundary."""
    def __init__(self, frames=(), blocked=False):
        from types import SimpleNamespace
        self.frames = iter(frames)
        self.sent = []
        self.prepared = False
        self.aborted = asyncio.Event()
        self.closing = asyncio.Event()
        self.blocked = blocked
        self._req = SimpleNamespace(transport=SimpleNamespace(
            abort=self.aborted.set, get_write_buffer_size=lambda:0))

    async def prepare(self, request):
        self.prepared = True

    async def receive(self):
        from types import SimpleNamespace
        return SimpleNamespace(type=WSMsgType.TEXT, data=json.dumps(
            {'kind':'hello', 'version':1, 'csrf':'csrf-value'}))

    def __aiter__(self):
        return self

    async def __anext__(self):
        from types import SimpleNamespace
        try:
            frame = next(self.frames)
        except StopIteration:
            raise StopAsyncIteration
        return SimpleNamespace(type=WSMsgType.TEXT, data=json.dumps(frame))

    async def send_str(self, body):
        self.sent.append(json.loads(body))

    async def close(self, **kwargs):
        self.closing.set()
        if self.blocked:
            await asyncio.Event().wait()


class StateRegressionTests(unittest.IsolatedAsyncioTestCase):
    def make_app(self, controller=None):
        return create_app(SessionsFixture(), controller, origin='https://192.168.1.2:8443')

    async def run_socket(self, app, ws):
        from types import SimpleNamespace
        from unittest.mock import patch
        from hub.server import websocket, SESSION
        request = SimpleNamespace(app=app, cookies={SESSION:'valid'})
        with patch('hub.server.web.WebSocketResponse', return_value=ws):
            await websocket(request)

    async def test_reconnecting_cookie_keeps_120_frame_budget(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        app = self.make_app()
        with patch('hub.server.time', SimpleNamespace(monotonic=lambda:1000.)):
            first = MemorySocket([{'seq':n} for n in range(120)])
            await self.run_socket(app, first)
            second = MemorySocket([{'seq':121}])
            await self.run_socket(app, second)
        self.assertEqual(first.sent[1].get('kind'), 'ack')
        self.assertEqual(len(first.sent), 121)  # hello plus initial burst
        self.assertEqual(len(second.sent), 1)  # hello only: reconnect grants no credit

    async def test_disconnected_revoked_budgets_are_pruned(self):
        from hub.server import STATE, lifecycle
        app = self.make_app()
        state = app[STATE]
        state.buckets = {'revoked':(0, 0), 'controller':(42, 1000)}
        cycle = lifecycle(app)
        await anext(cycle)
        try:
            async with asyncio.timeout(.35):
                while 'revoked' in state.buckets:
                    await asyncio.sleep(.01)
            self.assertEqual(state.buckets['controller'], (42, 1000))
        finally:
            await cycle.aclose()

    async def test_finally_bounds_whole_close_and_aborts_transport(self):
        ws = MemorySocket(blocked=True)
        await asyncio.wait_for(self.run_socket(self.make_app(), ws), .35)
        self.assertTrue(ws.aborted.is_set())

    async def test_revoke_closes_concurrently_without_stalling_timer(self):
        from hub.controller import Controller
        from hub.server import STATE, lifecycle
        now = [0.]
        controller = Controller(clock=lambda:now[0])
        app = self.make_app(controller)
        state = app[STATE]
        sockets = [MemorySocket(blocked=True) for _ in range(8)]
        for i, ws in enumerate(sockets):
            state.sockets[ws] = ('revoked', str(i), 'bad')
        await controller.handle('controller', 'live', {'version':1,'seq':1,'kind':'take_control'})
        cycle = lifecycle(app)
        await anext(cycle)
        try:
            now[0] = 4.
            async with asyncio.timeout(.35):
                while controller.status['controlled']:
                    await asyncio.sleep(.01)
            await asyncio.wait_for(asyncio.gather(*(ws.aborted.wait() for ws in sockets)), .35)
        finally:
            await asyncio.wait_for(cycle.aclose(), .35)

    async def test_shutdown_closes_all_with_one_deadline_and_fences_lease_first(self):
        from hub.controller import Controller
        from hub.server import STATE, lifecycle
        controller = Controller()
        app = self.make_app(controller)
        sockets = [MemorySocket(blocked=True) for _ in range(8)]
        for i, ws in enumerate(sockets):
            app[STATE].sockets[ws] = ('controller', str(i), 'valid')
        await controller.handle('controller', '0', {'version':1,'seq':1,'kind':'take_control'})
        cycle = lifecycle(app)
        await anext(cycle)
        closing = asyncio.create_task(cycle.aclose())
        await sockets[0].closing.wait()
        self.assertFalse(controller.status['controlled'])
        await asyncio.wait_for(closing, .35)
        self.assertTrue(all(ws.aborted.is_set() for ws in sockets))

    async def test_native_stop_sends_private_changed_status_only_after_hello(self):
        from hub.controller import Controller
        from hub.server import lifecycle, STATE
        class LiveSocket(MemorySocket):
            def __init__(self):
                super().__init__()
                self.hello = asyncio.Event()
                self.incoming = asyncio.Queue()
            async def receive(self):
                await self.hello.wait()
                return await super().receive()
            async def __anext__(self):
                from types import SimpleNamespace
                frame = await self.incoming.get()
                if frame is None:
                    raise StopAsyncIteration
                return SimpleNamespace(type=WSMsgType.TEXT, data=json.dumps(frame))
        controller = Controller()
        app = self.make_app(controller)
        cycle = lifecycle(app)
        await anext(cycle)
        owner, observer = LiveSocket(), LiveSocket()
        # Prepare each handler under its own constructor patch before starting another.
        first = asyncio.create_task(self.run_socket(app, owner))
        await asyncio.sleep(.15)
        self.assertEqual(owner.sent, [])
        owner.hello.set()
        async with asyncio.timeout(.35):
            while not owner.sent:
                await asyncio.sleep(.01)
        second = asyncio.create_task(self.run_socket(app, observer))
        observer.hello.set()
        try:
            async with asyncio.timeout(.35):
                while not observer.sent:
                    await asyncio.sleep(.01)
            await owner.incoming.put({'version':1,'seq':1,'kind':'take_control'})
            async with asyncio.timeout(.35):
                while len(owner.sent) < 2:
                    await asyncio.sleep(.01)
            lease = owner.sent[1]['lease']
            await asyncio.sleep(.15)
            self.assertEqual(len(owner.sent), 2)  # no duplicate take-control status
            self.assertNotIn(lease, json.dumps(observer.sent))
            controller.stop('native_stop')
            async with asyncio.timeout(.35):
                while len(owner.sent) < 3:
                    await asyncio.sleep(.01)
            self.assertEqual(owner.sent[2], {'kind':'status','available':False,'connected':False,'lease':None})
            await asyncio.sleep(.15)
            self.assertEqual(len(owner.sent), 3)
            self.assertNotIn(lease, json.dumps(observer.sent))
            self.assertLessEqual(len(app[STATE].snapshots), 8)
        finally:
            await owner.incoming.put(None)
            await observer.incoming.put(None)
            await asyncio.gather(first, second)
            await cycle.aclose()

    async def test_close_during_hello_send_does_not_restore_ready_snapshot(self):
        from hub.server import STATE
        entered, release = asyncio.Event(), asyncio.Event()
        class SlowHello(MemorySocket):
            async def send_str(self, body):
                entered.set()
                await release.wait()
                await super().send_str(body)
        app = self.make_app()
        ws = SlowHello()
        serving = asyncio.create_task(self.run_socket(app, ws))
        try:
            await entered.wait()
            await app[STATE].revoke('controller')
            await ws.closing.wait()
            await asyncio.sleep(.01)
            release.set()
            await serving
            self.assertEqual(app[STATE].snapshots, {})
            self.assertEqual(app[STATE].sockets, {})
        finally:
            release.set()
            await serving
