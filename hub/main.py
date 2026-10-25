"""Native owner entry point; browser-only H1B, with no device backend."""
import argparse
import asyncio
import contextlib
import ipaddress
import queue
import socket
import threading
import time
from pathlib import Path


PRIVATE = tuple(ipaddress.ip_network(value) for value in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16'))


def parse_address(value):
    address = ipaddress.IPv4Address(value)
    if not any(address in network for network in PRIVATE):
        raise ValueError('A reserved private IPv4 address is required')
    return address


class OwnerBridge:
    def __init__(self):
        self.commands = queue.Queue(maxsize=16)
        self.snapshots = queue.Queue(maxsize=1)
        self.stop_requested = threading.Event()
        self.shutdown_requested = threading.Event()

    def submit(self, action, value=None):
        try:
            self.commands.put_nowait((action, value))
            return True
        except queue.Full:
            return False

    def stop(self):
        self.stop_requested.set()

    def publish(self, value):
        with contextlib.suppress(queue.Empty):
            self.snapshots.get_nowait()
        self.snapshots.put_nowait(value)

    def latest(self):
        try:
            return self.snapshots.get_nowait()
        except queue.Empty:
            return None


class OwnerRuntime:
    def __init__(self, bridge, state_dir, address):
        self.bridge, self.state_dir, self.address = bridge, state_dir, address
        self.site = self.sessions = self.controller = None
        self.message = 'Stopped. Create an identity once, then start HTTPS.'
        self.fingerprint = ''
        self.identity = None
        self.next_refresh = 0.0
        self.identity_generation = 0
        self.identity_job = None
        self.identity_worker = None
        self.identity_results = queue.Queue(maxsize=1)

    async def serve(self):
        from .identity import Identity
        self.identity = Identity(self.state_dir / 'identity')
        try:
            while not self.bridge.shutdown_requested.is_set():
                if self.bridge.stop_requested.is_set():
                    self.bridge.stop_requested.clear()
                    self.identity_generation += 1
                    if self.controller:
                        self.controller.stop('native_stop')
                    self.message = 'Control stopped. TV integration remains locked.'
                for _ in range(16):
                    try:
                        action, value = self.bridge.commands.get_nowait()
                    except queue.Empty:
                        break
                    try:
                        await self.command(action, value)
                    except Exception:
                        self.message = 'Action failed. Check address, Keychain access and setup instructions.'
                try:
                    await self.finish_identity()
                    if self.site and self.identity_job is None and time.monotonic() >= self.next_refresh:
                        await self.refresh_tls()
                except Exception:
                    self.message = 'Action failed. Check address, Keychain access and setup instructions.'
                self.bridge.publish({'message':self.message, 'fingerprint':self.fingerprint,
                    'running': self.site is not None,
                    'pending': self.sessions.list_pending() if self.sessions else [],
                    'sessions': self.sessions.list_sessions() if self.sessions else []})
                await asyncio.sleep(.1)
        finally:
            await self.shutdown()

    def start_identity(self, action, value=None):
        if self.identity_job is not None:
            self.message = 'Identity operation busy. Wait for the current operation to finish.'
            return
        generation = self.identity_generation
        identity, address = self.identity, self.address
        self.identity_job = (generation, action)
        def work():
            context, fingerprint, failed = None, '', False
            try:
                if action == 'create':
                    identity.create_identity(address)
                    fingerprint = identity.root_fingerprint
                elif action == 'export':
                    fingerprint = identity.export_root(Path(value))
                else:
                    context = identity.load_tls_context(address)
                    if action == 'start':
                        fingerprint = identity.root_fingerprint
            except Exception:
                failed = True
            self.identity_results.put_nowait((generation, action, context, fingerprint, failed))
        # A blocked native Keychain call must not hold asyncio.run/process shutdown open.
        self.identity_worker = threading.Thread(target=work, name='ott-identity', daemon=True)
        self.identity_worker.start()
        self.message = 'Identity operation in progress.'

    async def finish_identity(self):
        if self.identity_job is None or self.identity_worker.is_alive():
            return
        generation, action, context, fingerprint, failed = self.identity_results.get_nowait()
        job = self.identity_job
        self.identity_job = self.identity_worker = None
        if (job != (generation, action) or generation != self.identity_generation
                or self.bridge.shutdown_requested.is_set() or self.bridge.stop_requested.is_set()):
            return
        if failed:
            if action == 'refresh':
                await self.shutdown()
                self.message = 'Certificate refresh failed. HTTPS stopped; check Keychain and clock.'
            else:
                self.message = 'Action failed. Check address, Keychain access and setup instructions.'
            return
        if action == 'create':
            self.fingerprint = fingerprint
            self.message = 'Identity created. Export the root and verify its fingerprint before trusting it.'
        elif action == 'export':
            self.fingerprint = fingerprint
            self.message = 'Root exported. Compare this full fingerprint in the receiving device certificate UI.'
        elif action == 'refresh':
            if self.site:
                self.site.tls = context
                self.next_refresh = time.monotonic() + 3600
                self.message = 'HTTPS certificate refreshed.'
        elif action == 'start':
            from .controller import Controller
            from .sessions import Sessions
            from .server import BoundedSite, create_app
            self.sessions = Sessions(self.state_dir / 'controllers.sqlite')
            self.controller = Controller()
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            try:
                sock.bind((str(self.address),8443))
                app = create_app(self.sessions, self.controller, origin=f'https://{self.address}:8443')
                self.site = BoundedSite(app, sock, context)
                await self.site.start()
                self.next_refresh = time.monotonic() + 3600
                self.fingerprint = fingerprint
            except Exception:
                sock.close()
                await self.shutdown()
                raise
            self.message = 'HTTPS enrollment ready. TV integration locked pending protected-link verification.'

    async def command(self, action, value):
        if action in ('create', 'export', 'start'):
            if self.identity_job is not None:
                self.message = 'Identity operation busy. Wait for the current operation to finish.'
            elif action == 'create' and self.site:
                raise ValueError('stop server first')
            elif action != 'start' or not self.site:
                self.start_identity(action, value)
        elif action == 'stop_server':
            await self.shutdown()
            self.message = 'HTTPS stopped.'
        elif action == 'open_window' and self.sessions:
            self.sessions.open_window()
            self.message = 'Enrollment open for 120 seconds. Approve only a matching code.'
        elif action == 'approve' and self.sessions:
            self.sessions.approve(value)
        elif action == 'cancel' and self.sessions:
            self.sessions.cancel(value)
        elif action == 'revoke' and self.sessions:
            self.sessions.revoke(value)
            if self.site:
                from .server import STATE
                await self.site.app[STATE].revoke(value)
            else:
                self.controller.revoke(value)
        elif action == 'revoke_all' and self.sessions:
            self.sessions.revoke_all()
            self.controller.stop('revoked')
            if self.site:
                from .server import STATE
                state = self.site.app[STATE]
                for sid in {owner for owner, _, _ in state.sockets.values()} | set(state.buckets):
                    await state.revoke(sid)
        elif action != 'snapshot':
            raise ValueError('unsupported owner action')

    async def refresh_tls(self):
        if self.site:
            self.start_identity('refresh')

    async def shutdown(self):
        self.identity_generation += 1
        if self.controller:
            self.controller.stop('shutdown')
        if self.site:
            await self.site.close()
            self.site = None
        if self.sessions:
            self.sessions.close()
            self.sessions = None
        self.controller = None


def main():
    parser = argparse.ArgumentParser(description='Open TV Touchpad — browser-only local hub')
    parser.add_argument('--address', required=True, type=parse_address, help='Reserved RFC1918 IPv4 address of this Mac')
    parser.add_argument('--state-dir', type=Path, default=Path.home()/'Library/Application Support/Open TV Touchpad')
    args = parser.parse_args()
    # Identity owns creation/validation of its own private directory. Do not modify a shared parent.
    args.state_dir.mkdir(parents=True, mode=0o700, exist_ok=True)
    if args.state_dir.is_symlink() or args.state_dir.stat().st_mode & 0o077:
        parser.error('State directory must be a private owner-only directory')
    from .local_ui import OwnerWindow
    import tkinter as tk
    bridge = OwnerBridge()
    runtime = OwnerRuntime(bridge, args.state_dir, args.address)
    def run():
        try:
            asyncio.run(runtime.serve())
        except Exception:
            bridge.publish({'message':'Hub stopped. Check setup and Keychain access.', 'running':False,
                            'fingerprint':'', 'pending':[], 'sessions':[]})
    worker = threading.Thread(target=run, name='ott-hub', daemon=False)
    root = tk.Tk()
    OwnerWindow(root, bridge, str(args.address), worker)
    worker.start()
    root.mainloop()
    worker.join()


if __name__ == '__main__':
    main()
