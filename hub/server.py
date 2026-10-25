"""Same-origin browser gate. No projector backend is composed here."""
import asyncio
import contextlib
import hmac
import json
import logging
import secrets
import socket
import time
from pathlib import Path
from urllib.parse import urlsplit

from aiohttp import web, WSMsgType

SESSION = '__Host-ott_session'
PENDING = '__Host-ott_pending'
WEB = Path(__file__).resolve().parent.parent / 'web'
STATIC = {'/': ('index.html', 'text/html'), '/remote.js': ('remote.js', 'text/javascript'),
          '/gestures.js': ('gestures.js', 'text/javascript'), '/style.css': ('style.css', 'text/css'),
          '/manifest.webmanifest': ('manifest.webmanifest', 'application/manifest+json'),
          '/sw.js': ('sw.js', 'text/javascript'), '/icons/icon.svg': ('icons/icon.svg', 'image/svg+xml')}
STATE = web.AppKey('state', object)
LOG = logging.getLogger('ott.http')
LOG.addHandler(logging.NullHandler())
LOG.propagate = False


def decode_json(raw):
    def pairs(items):
        out = {}
        for key, value in items:
            if key in out:
                raise ValueError('duplicate field')
            out[key] = value
        return out
    def invalid(_):
        raise ValueError('invalid number')
    try:
        value = json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid)
    except (TypeError, UnicodeError, RecursionError, json.JSONDecodeError) as exc:
        raise ValueError('invalid JSON') from exc
    if type(value) is not dict:
        raise ValueError('object required')
    return value


class _State:
    def __init__(self, sessions, controller, origin):
        self.sessions, self.controller, self.origin = sessions, controller, origin
        self.authority = urlsplit(origin).netloc
        self.sockets = {}  # socket -> (session, socket id, bearer); bounded at eight
        self.buckets = {}
        self.snapshots = {}  # only successfully greeted sockets, at most eight
        self.socket_locks = {}
        self.status_tasks = {}
        self.close_tasks = {}
        self.site = None

    def status(self):
        # H1B intentionally has no real backend, regardless of local test recorders.
        return {'available': False, 'connected': False}

    def auth(self, request):
        token = request.cookies.get(SESSION, '')
        try:
            return self.sessions.authenticate(token)
        except ValueError:
            raise web.HTTPUnauthorized(text='Enrollment required') from None

    def check_csrf(self, request, sid):
        if not hmac.compare_digest(request.headers.get('X-CSRF-Token', ''), self.sessions.csrf(sid)):
            raise web.HTTPForbidden(text='Invalid request')

    def prune_buckets(self):
        active = {row['id'] for row in self.sessions.list_sessions()}
        for sid in self.buckets.keys() - active:
            self.buckets.pop(sid, None)

    def allow_frame(self, sid):
        if sid not in self.buckets:
            self.prune_buckets()
        now = time.monotonic()
        credit, last = self.buckets.get(sid, (120., now))
        credit = min(120., credit + (now-last)*60)
        self.buckets[sid] = (max(0, credit-1), now)
        return credit >= 1

    async def send(self, ws, data):
        body = json.dumps(data, separators=(',', ':'), allow_nan=False)
        transport = ws._req.transport
        if len(body.encode()) > 4096 or transport is None or transport.get_write_buffer_size()+len(body.encode()) > 32768:
            raise ConnectionError('slow client')
        await asyncio.wait_for(ws.send_str(body), .2)

    def socket_status(self, sid, socket_id):
        status = self.controller.status_for(sid, socket_id) if self.controller else {'lease':None}
        # H1B never advertises a device backend, even with a test controller.
        return {**status, **self.status()}

    def publish_status(self):
        for ws in list(self.snapshots):
            if ws in self.close_tasks or ws in self.status_tasks:
                continue
            sid, socket_id, _ = self.sockets[ws]
            if self.socket_status(sid, socket_id) != self.snapshots[ws]:
                task = asyncio.create_task(self.send_status(ws))
                self.status_tasks[ws] = task
                task.add_done_callback(lambda _, ws=ws: self.status_tasks.pop(ws, None))

    async def send_status(self, ws):
        try:
            async with self.socket_locks[ws]:
                if ws not in self.snapshots or ws in self.close_tasks:
                    return
                sid, socket_id, _ = self.sockets[ws]
                status = self.socket_status(sid, socket_id)
                if status != self.snapshots[ws]:
                    await self.send(ws, {'kind':'status', **status})
                    if ws in self.snapshots and ws not in self.close_tasks:
                        self.snapshots[ws] = status
        except (TimeoutError, ConnectionError):
            self.close_socket(ws)

    def close_socket(self, ws, code=1008):
        if ws in self.close_tasks:
            return self.close_tasks[ws]
        if ws not in self.sockets:
            return None
        if self.controller:
            self.controller.socket_closed(self.sockets[ws][1])
        self.snapshots.pop(ws, None)
        task = asyncio.create_task(self.close_ws(ws, code))
        self.close_tasks[ws] = task
        return task

    async def close_ws(self, ws, code):
        transport = ws._req.transport if ws._req is not None else None
        try:
            # aiohttp's own timeout starts after drain; bound the entire close.
            await asyncio.wait_for(ws.close(code=code), .2)
        except (Exception, asyncio.CancelledError):
            if transport is not None:
                transport.abort()
        finally:
            status_task = self.status_tasks.pop(ws, None)
            if status_task:
                status_task.cancel()
            self.sockets.pop(ws, None)
            self.socket_locks.pop(ws, None)
            self.close_tasks.pop(ws, None)

    async def revoke(self, sid):
        if self.controller:
            self.controller.revoke(sid)
        self.buckets.pop(sid, None)
        for ws, (owner, _, _) in list(self.sockets.items()):
            if owner == sid:
                self.close_socket(ws)


@web.middleware
async def boundary(request, handler):
    state = request.app[STATE]
    if state.site:
        state.site.header_complete(request.transport)
    try:
        if len(request.headers.getall('Host', [])) != 1 or request.headers['Host'] != state.authority:
            raise web.HTTPForbidden(text='Invalid host')
        if request.query_string:
            raise web.HTTPBadRequest(text='Query parameters unsupported')
        if request.method != 'GET' or request.path == '/ws':
            if request.headers.getall('Origin', []) != [state.origin]:
                raise web.HTTPForbidden(text='Invalid origin')
        if request.headers.get('Content-Encoding'):
            raise web.HTTPUnsupportedMediaType(text='Encoding unsupported')
        if request.content_length is not None and request.content_length > 4096:
            raise web.HTTPRequestEntityTooLarge(max_size=4096, actual_size=request.content_length)
        response = await handler(request)
    except web.HTTPException as exc:
        response = exc
    except (ValueError, UnicodeError):
        response = web.Response(status=400, text='Invalid request')
    except (TimeoutError, ConnectionError):
        response = web.Response(status=408, text='Request timed out')
    except Exception:
        # Never emit request contents, cookies or underlying exception text.
        response = web.Response(status=500, text='Request failed')
    if not response.prepared:
        response.headers.update({'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff',
            'Referrer-Policy':'no-referrer', 'Content-Security-Policy':
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
            f"connect-src 'self' {state.origin.replace('https:', 'wss:', 1)}; "
            "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"})
        response.force_close()
    return response


async def body(request, fields):
    if request.content_type != 'application/json':
        raise web.HTTPUnsupportedMediaType(text='JSON required')
    raw = await asyncio.wait_for(request.read(), 5)
    value = decode_json(raw)
    if set(value) != set(fields):
        raise ValueError('fields')
    return value


async def static(request):
    name, mime = STATIC[request.path]
    path = WEB / name
    if not path.is_file():
        raise web.HTTPNotFound()
    return web.Response(body=path.read_bytes(), content_type=mime)


async def enroll_request(request):
    data = await body(request, {'label'})
    state = request.app[STATE]
    token, csrf, code = state.sessions.request(data['label'])
    result = web.json_response({'csrf':csrf, 'code':code, 'expires':120})
    result.set_cookie(PENDING, token, max_age=120, secure=True, httponly=True, samesite='Strict', path='/')
    return result


async def enroll_complete(request):
    await body(request, set())
    token = request.app[STATE].sessions.complete(request.cookies.get(PENDING, ''),
                                                request.headers.get('X-CSRF-Token', ''))
    if token is None:
        return web.json_response({'status':'pending'}, status=202)
    response = web.json_response({'status':'enrolled'})
    response.set_cookie(SESSION, token, max_age=30*86400, secure=True, httponly=True, samesite='Strict', path='/')
    response.del_cookie(PENDING, path='/', secure=True, httponly=True, samesite='Strict')
    return response


async def session(request):
    state = request.app[STATE]
    sid = state.auth(request)
    record = next(row for row in state.sessions.list_sessions() if row['id'] == sid)
    return web.json_response({'csrf':state.sessions.csrf(sid), 'session':record, 'status':state.status()})


async def logout(request):
    state = request.app[STATE]
    sid = state.auth(request)
    state.check_csrf(request, sid)
    await body(request, set())
    state.sessions.revoke(sid)
    await state.revoke(sid)
    response = web.json_response({'status':'signed_out'})
    response.del_cookie(SESSION, path='/', secure=True, httponly=True, samesite='Strict')
    return response


async def websocket(request):
    state = request.app[STATE]
    sid = state.auth(request)
    if len(state.sockets) >= 8:
        raise web.HTTPServiceUnavailable(text='Connection limit')
    ws = web.WebSocketResponse(max_msg_size=4096, compress=False, timeout=.2, autoping=False, writer_limit=32768)
    socket_id = secrets.token_hex(16)
    state.sockets[ws] = (sid, socket_id, request.cookies[SESSION])
    lock = state.socket_locks[ws] = asyncio.Lock()
    try:
        await ws.prepare(request)
        message = await asyncio.wait_for(ws.receive(), 3)
        if message.type != WSMsgType.TEXT:
            raise ValueError('hello required')
        hello = decode_json(message.data)
        if set(hello) != {'version','kind','csrf'} or type(hello['version']) is not int or hello['version'] != 1 or hello['kind'] != 'hello' or not isinstance(hello['csrf'],str):
            raise ValueError('hello invalid')
        if not hmac.compare_digest(hello['csrf'],state.sessions.csrf(sid)):
            raise ValueError('hello invalid')
        async with lock:
            if ws in state.close_tasks or ws not in state.sockets:
                raise ValueError('session ended')
            status = state.socket_status(sid, socket_id)
            await state.send(ws, {'kind':'hello', 'version':1, 'status':status})
            if ws in state.close_tasks or ws not in state.sockets:
                raise ValueError('session ended')
            state.snapshots[ws] = status
        async for message in ws:
            # Absolute expiry and revoke checked on every received frame, not cookie lifetime.
            if state.sessions.authenticate(request.cookies[SESSION]) != sid or not state.allow_frame(sid):
                raise ValueError('session or limit')
            if message.type != WSMsgType.TEXT:
                raise ValueError('text required')
            data = decode_json(message.data)
            async with lock:
                if ws in state.close_tasks or ws not in state.sockets:
                    raise ValueError('session ended')
                if state.controller:
                    ack = await state.controller.handle(sid, socket_id, data)
                else:
                    ack = {'kind':'ack', 'seq':data.get('seq'), 'status':'failed', 'error':'unavailable'}
                status = state.socket_status(sid, socket_id)
                await state.send(ws, ack)
                if ws not in state.close_tasks and ws in state.sockets:
                    state.snapshots[ws] = status

    except (ValueError, TimeoutError, ConnectionError):
        pass
    finally:
        closing = state.close_socket(ws)
        if closing:
            await asyncio.shield(closing)
    return ws


async def lifecycle(app):
    state = app[STATE]
    async def watch():
        while True:
            await asyncio.sleep(.1)
            for sid in state.sessions.expire():
                await state.revoke(sid)
            state.prune_buckets()
            # Local revocation can happen between expiry scans.
            for ws, (sid, _, token) in list(state.sockets.items()):
                try:
                    state.sessions.authenticate(token)
                except ValueError:
                    await state.revoke(sid)
            if state.controller:
                state.controller.tick()
            state.publish_status()
    task = asyncio.create_task(watch())
    try:
        yield
    finally:
        if state.controller:
            state.controller.stop('shutdown')
        task.cancel()
        closing = [state.close_socket(ws, code=1001) for ws in list(state.sockets)]
        with contextlib.suppress(asyncio.CancelledError):
            await task
        await asyncio.gather(*closing)
        if state.controller:
            await state.controller.close()


def create_app(sessions, controller=None, *, origin):
    parsed = urlsplit(origin)
    if parsed.scheme != 'https' or parsed.path or parsed.query or parsed.fragment or parsed.username or parsed.port != 8443:
        raise ValueError('canonical HTTPS origin required')
    app = web.Application(middlewares=[boundary], client_max_size=4096)
    app[STATE] = _State(sessions, controller, origin)
    for path in STATIC:
        app.router.add_get(path, static, allow_head=False)
    app.router.add_post('/api/enroll/request', enroll_request)
    app.router.add_post('/api/enroll/complete', enroll_complete)
    app.router.add_get('/api/session', session, allow_head=False)
    app.router.add_post('/api/logout', logout)
    app.router.add_get('/ws', websocket, allow_head=False)
    app.cleanup_ctx.append(lifecycle)
    return app


class _Protocol(asyncio.Protocol):
    """Delegate HTTP parsing to aiohttp; own only connection budget/deadline."""
    def __init__(self, site, inner, raw):
        self.site, self.inner, self.raw = site, inner, raw
        self.transport = None
        self.timer = None

    def connection_made(self, transport):
        self.transport = transport
        self.site.transports[transport] = self
        transport.set_write_buffer_limits(high=32768, low=16384)
        self.timer = asyncio.get_running_loop().call_later(5, transport.abort)
        self.inner.connection_made(transport)

    def data_received(self, data):
        self.inner.data_received(data)

    def eof_received(self):
        return self.inner.eof_received()

    def pause_writing(self):
        self.inner.pause_writing()

    def resume_writing(self):
        self.inner.resume_writing()

    def connection_lost(self, exc):
        if self.timer:
            self.timer.cancel()
        self.site.transports.pop(self.transport, None)
        self.site.sockets.discard(self.raw)
        self.inner.connection_lost(exc)


class BoundedSite:
    """32 accepted sockets total, including handshakes, using standard asyncio TLS."""
    def __init__(self, app, sock, tls):
        if tls is None:
            raise ValueError('TLS required')
        self.app, self.sock, self.tls = app, sock, tls
        self.runner = web.AppRunner(app, access_log=None, logger=LOG, handler_cancellation=True,
                                    max_line_size=2048, max_field_size=2048, max_headers=32,
                                    read_bufsize=8192, auto_decompress=False, lingering_time=0)
        self.sockets, self.handshakes = set(), set()
        self.transports = {}
        self.accept_task = None
        app[STATE].site = self

    async def start(self):
        await self.runner.setup()
        self.sock.setblocking(False)
        self.sock.listen(32)
        self.accept_task = asyncio.create_task(self._accept())

    async def _accept(self):
        loop = asyncio.get_running_loop()
        while True:
            raw, _ = await loop.sock_accept(self.sock)
            if len(self.sockets) >= 32:
                raw.close()
                continue
            self.sockets.add(raw)
            task = asyncio.create_task(self._handshake(raw))
            self.handshakes.add(task)
            task.add_done_callback(self.handshakes.discard)

    async def _handshake(self, raw):
        try:
            await asyncio.get_running_loop().connect_accepted_socket(
                lambda: _Protocol(self, self.runner.server(), raw), raw,
                ssl=self.tls, ssl_handshake_timeout=5, ssl_shutdown_timeout=.2)
        except (OSError, TimeoutError, asyncio.CancelledError):
            self.sockets.discard(raw)
            raw.close()

    def header_complete(self, transport):
        protocol = self.transports.get(transport)
        if protocol and protocol.timer:
            protocol.timer.cancel()
            protocol.timer = None

    async def close(self):
        if self.app[STATE].controller:
            self.app[STATE].controller.stop('shutdown')
        if self.accept_task:
            self.accept_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self.accept_task
        self.sock.close()
        tasks = list(self.handshakes)
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        for transport in list(self.transports):
            transport.abort()
        await self.runner.cleanup()
        self.sockets.clear()
