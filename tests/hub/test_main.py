import unittest
from hub.main import parse_address, OwnerBridge


class SetupTests(unittest.TestCase):
    def test_only_rfc1918_addresses_are_accepted(self):
        for value in ['127.0.0.1','0.0.0.0','169.254.1.2','192.0.2.1','8.8.8.8','::1','projector.local']:
            with self.assertRaises(ValueError): parse_address(value)
        self.assertEqual(str(parse_address('192.168.1.2')),'192.168.1.2')

    def test_native_queue_is_bounded_and_stop_is_independent(self):
        bridge=OwnerBridge()
        for _ in range(16): self.assertTrue(bridge.submit('snapshot'))
        self.assertFalse(bridge.submit('snapshot'))
        bridge.stop()
        self.assertTrue(bridge.stop_requested.is_set())
        bridge.publish({'value':1})
        bridge.publish({'value':2})
        self.assertEqual(bridge.latest(),{'value':2})
        self.assertIsNone(bridge.latest())


class RuntimeTests(unittest.IsolatedAsyncioTestCase):
    async def test_refresh_replaces_context_without_changing_origin(self):
        from hub.main import OwnerRuntime
        from pathlib import Path
        class IdentityFixture:
            def load_tls_context(self,address):
                self.address=address
                return 'renewed'
        class SiteFixture:
            tls='old'
        runtime=OwnerRuntime(OwnerBridge(),Path('/unused'),parse_address('192.168.1.2'))
        runtime.identity=IdentityFixture()
        runtime.site=SiteFixture()
        import asyncio
        await runtime.refresh_tls()
        async with asyncio.timeout(1):
            while runtime.identity_job is not None:
                await runtime.finish_identity()
                await asyncio.sleep(.01)
        self.assertEqual(runtime.site.tls,'renewed')
        self.assertEqual(str(runtime.identity.address),'192.168.1.2')

    async def test_owner_approval_and_revocation_reach_sessions(self):
        import tempfile
        from pathlib import Path
        from hub.main import OwnerRuntime
        from hub.sessions import Sessions
        from hub.controller import Controller
        with tempfile.TemporaryDirectory() as tmp:
            runtime=OwnerRuntime(OwnerBridge(),Path(tmp).resolve(),parse_address('192.168.1.2'))
            runtime.sessions=Sessions(Path(tmp).resolve()/'controllers.sqlite')
            runtime.controller=Controller()
            try:
                await runtime.command('open_window',None)
                pending,csrf,_=runtime.sessions.request('Phone')
                await runtime.command('approve',runtime.sessions.list_pending()[0]['pending_id'])
                token=runtime.sessions.complete(pending,csrf)
                sid=runtime.sessions.authenticate(token)
                ack=await runtime.controller.handle(sid,'socket',{'version':1,'seq':1,'kind':'take_control'})
                self.assertIn('lease',ack)
                await runtime.command('revoke',sid)
                self.assertFalse(runtime.controller.status['controlled'])
                with self.assertRaises(ValueError):runtime.sessions.authenticate(token)
            finally:
                await runtime.controller.close()
                runtime.sessions.close()


class IdentityJobTests(unittest.IsolatedAsyncioTestCase):
    async def test_blocked_identity_keeps_stop_revoke_and_shutdown_responsive(self):
        import asyncio
        import tempfile
        import threading
        import time
        from pathlib import Path
        from unittest.mock import patch
        from hub.main import OwnerRuntime
        from hub.controller import Controller
        from hub.sessions import Sessions
        from hub.server import create_app

        for action in ('create', 'export', 'start', 'refresh'):
            with self.subTest(action=action), tempfile.TemporaryDirectory() as tmp:
                entered, release = threading.Event(), threading.Event()
                calls = []
                class IdentityFixture:
                    root_fingerprint = 'test fingerprint'
                    def operation(self, value):
                        calls.append(action)
                        entered.set()
                        release.wait()
                        return 'late context'
                    create_identity = export_root = load_tls_context = operation
                class SiteFixture:
                    tls = 'old context'
                    async def close(self):
                        pass
                runtime = OwnerRuntime(OwnerBridge(), Path(tmp).resolve(), parse_address('192.168.1.2'))
                sessions = runtime.sessions = Sessions(runtime.state_dir/'controllers.sqlite')
                controller = runtime.controller = Controller()
                sessions.open_window()
                pending, csrf, _ = sessions.request('Phone')
                sessions.approve(sessions.list_pending()[0]['pending_id'])
                token = sessions.complete(pending, csrf)
                sid = sessions.authenticate(token)
                site = SiteFixture()
                site.app = create_app(sessions, controller, origin='https://192.168.1.2:8443')
                if action in ('export', 'refresh'):
                    runtime.site = site
                    runtime.next_refresh = 0 if action == 'refresh' else time.monotonic()+3600
                if action != 'refresh':
                    runtime.bridge.submit(action, str(runtime.state_dir/'root.cer'))
                with patch('hub.identity.Identity', return_value=IdentityFixture()):
                    serving = asyncio.create_task(runtime.serve())
                    try:
                        async with asyncio.timeout(1):
                            while not entered.is_set():
                                await asyncio.sleep(.01)
                        await controller.handle(sid, 'socket', {'version':1, 'seq':1, 'kind':'take_control'})
                        runtime.bridge.stop()
                        async with asyncio.timeout(.35):
                            while controller.status['controlled']:
                                await asyncio.sleep(.01)
                        runtime.bridge.submit('export', str(runtime.state_dir/'other.cer'))
                        async with asyncio.timeout(.35):
                            while 'busy' not in runtime.message.lower():
                                await asyncio.sleep(.01)
                        self.assertEqual(len(calls), 1)
                        runtime.bridge.submit('revoke', sid)
                        async with asyncio.timeout(.35):
                            while sessions.list_sessions():
                                await asyncio.sleep(.01)
                        runtime.bridge.submit('stop_server')
                        async with asyncio.timeout(.35):
                            while runtime.sessions is not None:
                                await asyncio.sleep(.01)
                        runtime.bridge.shutdown_requested.set()
                        await asyncio.wait_for(asyncio.shield(serving), .35)
                        self.assertFalse(release.is_set())
                        release.set()
                        await asyncio.sleep(.05)
                        self.assertIsNone(runtime.site)
                        self.assertEqual(site.tls, 'old context')
                        self.assertEqual(runtime.fingerprint, '')
                    finally:
                        release.set()
                        runtime.bridge.shutdown_requested.set()
                        await asyncio.wait_for(serving, 2)
                        await controller.close()
                        sessions.close()

    async def test_asyncio_run_exits_before_identity_unblocks(self):
        import asyncio
        import threading
        from pathlib import Path
        from unittest.mock import patch
        from hub.main import OwnerRuntime
        entered, release, exited = threading.Event(), threading.Event(), threading.Event()
        class IdentityFixture:
            root_fingerprint = 'test fingerprint'
            def load_tls_context(self, address):
                entered.set()
                release.wait()
                return 'late context'
        runtime = OwnerRuntime(OwnerBridge(), Path('/unused'), parse_address('192.168.1.2'))
        runtime.bridge.submit('start')
        def run():
            try:
                asyncio.run(runtime.serve())
            finally:
                exited.set()
        with patch('hub.identity.Identity', return_value=IdentityFixture()):
            worker = threading.Thread(target=run)
            worker.start()
            try:
                async with asyncio.timeout(1):
                    while not entered.is_set():
                        await asyncio.sleep(.01)
                self.assertTrue(runtime.identity_worker.daemon)
                runtime.bridge.shutdown_requested.set()
                async with asyncio.timeout(.35):
                    while not exited.is_set():
                        await asyncio.sleep(.01)
            finally:
                release.set()
                worker.join(2)


    async def test_late_start_and_refresh_results_cannot_replace_stopped_generation(self):
        import asyncio
        import threading
        from pathlib import Path
        from unittest.mock import patch
        from hub.main import OwnerRuntime
        for action in ('start', 'refresh'):
            with self.subTest(action=action):
                entered, release = threading.Event(), threading.Event()
                class IdentityFixture:
                    root_fingerprint = 'test fingerprint'
                    def load_tls_context(self, address):
                        entered.set()
                        release.wait()
                        return 'obsolete context'
                class SiteFixture:
                    tls = 'replacement context'
                    async def close(self):
                        pass
                runtime = OwnerRuntime(OwnerBridge(), Path('/unused'), parse_address('192.168.1.2'))
                runtime.identity = IdentityFixture()
                if action == 'refresh':
                    runtime.site = SiteFixture()
                    await runtime.refresh_tls()
                else:
                    await runtime.command('start', None)
                try:
                    async with asyncio.timeout(1):
                        while not entered.is_set():
                            await asyncio.sleep(.01)
                    await runtime.command('stop_server', None)
                    replacement = runtime.site = SiteFixture()
                    release.set()
                    with patch('hub.main.socket.socket', side_effect=AssertionError('stale start attempted bind')):
                        async with asyncio.timeout(1):
                            while runtime.identity_job is not None:
                                await runtime.finish_identity()
                                await asyncio.sleep(.01)
                    self.assertIs(runtime.site, replacement)
                    self.assertEqual(replacement.tls, 'replacement context')
                    self.assertEqual(runtime.fingerprint, '')
                finally:
                    release.set()
                    await runtime.shutdown()


    async def test_native_revoke_all_fences_and_closes_sockets_without_waiting_for_drain(self):
        import asyncio
        import tempfile
        from pathlib import Path
        from types import SimpleNamespace
        from hub.controller import Controller
        from hub.main import OwnerRuntime
        from hub.sessions import Sessions
        from hub.server import create_app, STATE
        class SocketFixture:
            def __init__(self):
                self.aborted = asyncio.Event()
                self._req = SimpleNamespace(transport=SimpleNamespace(abort=self.aborted.set))
            async def close(self, **kwargs):
                await asyncio.Event().wait()
        with tempfile.TemporaryDirectory() as tmp:
            runtime = OwnerRuntime(OwnerBridge(), Path(tmp).resolve(), parse_address('192.168.1.2'))
            sessions = runtime.sessions = Sessions(runtime.state_dir/'controllers.sqlite')
            controller = runtime.controller = Controller()
            app = create_app(sessions, controller, origin='https://192.168.1.2:8443')
            runtime.site = SimpleNamespace(app=app)
            sockets = []
            tokens = []
            try:
                sessions.open_window()
                for i in range(2):
                    pending, csrf, _ = sessions.request('Phone')
                    sessions.approve(sessions.list_pending()[0]['pending_id'])
                    token = sessions.complete(pending, csrf)
                    sid = sessions.authenticate(token)
                    tokens.append(token)
                    ws = SocketFixture()
                    sockets.append(ws)
                    app[STATE].sockets[ws] = (sid, str(i), token)
                    app[STATE].allow_frame(sid)
                await controller.handle(sid, '1', {'version':1,'seq':1,'kind':'take_control'})
                await asyncio.wait_for(runtime.command('revoke_all', None), .1)
                self.assertFalse(controller.status['controlled'])
                self.assertEqual(app[STATE].buckets, {})
                for token in tokens:
                    with self.assertRaises(ValueError):
                        sessions.authenticate(token)
                await asyncio.wait_for(asyncio.gather(*(ws.aborted.wait() for ws in sockets)), .35)
                self.assertEqual(app[STATE].sockets, {})
            finally:
                await controller.close()
                sessions.close()
