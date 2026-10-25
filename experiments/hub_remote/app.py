"""Disposable local Mac proof. This is not a PWA or a LAN server."""
import argparse
import asyncio
import logging
import tkinter as tk
from tkinter import ttk

import pointer
from tv_remote import TVRemote

_SETUP_ACTIONS = ('pair', 'confirm', 'reconnect', 'pointer')


class RemoteWindow:
    def __init__(self, root, loop, serial, host):
        self.root, self.loop, self.serial = root, loop, serial
        self.tv = TVRemote(host)
        self.pad = None
        self.tv_established = self.pad_established = False
        self.queue = asyncio.Queue(maxsize=8)
        self.epoch = 0
        self.closing = False
        self.destroyed = False
        self.busy = False
        self.setting_up = False
        self.tv_task = None
        self.last = None
        self.pending = [0., 0.]
        self.status = tk.StringVar(value='Local hardware proof — connect each control channel.')
        self.channel = tk.StringVar(value='TV remote: disconnected | Touchpad: disconnected')
        self.sensitivity = tk.DoubleVar(value=.6)
        root.title('Open TV Touchpad — Unified Remote Proof')
        root.geometry('540x690')
        root.minsize(520, 650)
        root.protocol('WM_DELETE_WINDOW', self.shutdown)
        box = ttk.Frame(root, padding=18)
        box.pack(fill='both', expand=True)
        ttk.Label(box, text='Open TV Touchpad', font=('', 20, 'bold')).pack(anchor='w')
        ttk.Label(box, text='Mac-only test · no screen streaming').pack(anchor='w')
        ttk.Label(box, textvariable=self.channel).pack(anchor='w', pady=10)
        pairing = ttk.Frame(box)
        pairing.pack(fill='x')
        ttk.Button(pairing, text='Pair TV', command=lambda: self.enqueue('pair')).pack(side='left')
        self.code = ttk.Entry(pairing, width=8, show='•')
        self.code.pack(side='left', padx=6)
        ttk.Button(pairing, text='Confirm code', command=self.confirm).pack(side='left')
        ttk.Button(pairing, text='Reconnect TV', command=lambda: self.enqueue('reconnect')).pack(side='left')
        keys = ttk.Frame(box)
        keys.pack(pady=12)
        for label, name, row, column in [
            ('Home', 'HOME', 0, 0), ('▲', 'UP', 0, 1), ('Back', 'BACK', 0, 2),
            ('◀', 'LEFT', 1, 0), ('OK', 'OK', 1, 1), ('▶', 'RIGHT', 1, 2),
            ('Vol −', 'VOLUME_DOWN', 2, 0), ('▼', 'DOWN', 2, 1), ('Vol +', 'VOLUME_UP', 2, 2),
        ]:
            ttk.Button(keys, text=label, width=10,
                       command=lambda n=name: self.enqueue('key', n)).grid(row=row, column=column, padx=3, pady=3)
        launch = ttk.Frame(box)
        launch.pack(fill='x')
        ttk.Button(launch, text='Netflix', command=lambda: self.enqueue('netflix')).pack(side='left')
        ttk.Button(launch, text='Play / Pause', command=lambda: self.enqueue('key', 'PLAY_PAUSE')).pack(side='left', padx=5)
        ttk.Button(launch, text='Connect touchpad', command=lambda: self.enqueue('pointer')).pack(side='right')
        self.surface = tk.Canvas(box, height=175, background='#202b37', highlightthickness=0)
        self.surface.pack(fill='both', expand=True, pady=12)
        self.surface.create_text(235, 60, fill='white', width=410,
                                 text='Move here to move the projector pointer\nClick to select · Hold and move to drag\nScroll buttons send mouse wheel input')
        self.surface.bind('<Motion>', self.motion)
        self.surface.bind('<ButtonPress-1>', lambda e: self.enqueue('press'))
        self.surface.bind('<ButtonRelease-1>', lambda e: self.enqueue('release'))
        self.surface.bind('<Leave>', self.leave)
        self.surface.bind('<Enter>', lambda e: setattr(self, 'last', (e.x, e.y)))
        scroll = ttk.Frame(box)
        scroll.pack()
        for text, dx, dy in [('Scroll ←', 1, 0), ('Scroll ↑', 0, 1), ('Scroll ↓', 0, -1), ('Scroll →', -1, 0)]:
            ttk.Button(scroll, text=text, command=lambda x=dx, y=dy: self.enqueue('scroll', x, y)).pack(side='left', padx=2)
        ttk.Label(box, text='Pointer sensitivity').pack(anchor='w', pady=(10, 0))
        ttk.Scale(box, from_=.2, to=1.5, variable=self.sensitivity).pack(fill='x')
        ttk.Button(box, text='STOP / Disconnect all', command=self.stop).pack(fill='x', pady=10)
        ttk.Label(box, textvariable=self.status, wraplength=490).pack(anchor='w')
        self.consumer = loop.create_task(self.consume())
        root.after(33, self.flush_motion)
        root.after(100, self.refresh)

    def confirm(self):
        code = self.code.get().strip()
        self.code.delete(0, 'end')
        self.enqueue('confirm', code)

    def motion(self, event):
        if not self.setting_up and self.last is not None and self.pad and self.pad.connected:
            scale = self.sensitivity.get()
            self.pending[0] = max(-256, min(256, self.pending[0] + (event.x - self.last[0]) * scale))
            self.pending[1] = max(-256, min(256, self.pending[1] + (event.y - self.last[1]) * scale))
        self.last = event.x, event.y

    def leave(self, _event):
        self.last = None
        if self.pad and self.pad.down:
            self.enqueue('release')

    def flush_motion(self):
        if self.closing:
            return
        # Do not buffer movement behind slow setup/network operations.
        if any(self.pending):
            if not self.busy and self.queue.empty():
                self.enqueue('move', *self.pending)
            self.pending[:] = [0., 0.]
        self.root.after(33, self.flush_motion)

    def enqueue(self, action, *args):
        if self.closing or self.setting_up:
            return
        if action in ('press', 'release', 'scroll') and any(self.pending):
            motion, self.pending = self.pending, [0., 0.]
            self.enqueue('move', *motion)
        try:
            self.queue.put_nowait((self.epoch, action, args))
            if action in _SETUP_ACTIONS:
                # Fence input from admission through setup completion, including pump gaps.
                self.setting_up = True
                self.pending[:] = [0., 0.]
        except asyncio.QueueFull:
            self.stop()
            self.status.set('Input overflow: stopped safely. Reconnect before continuing.')

    async def consume(self):
        while True:
            epoch, action, args = await self.queue.get()
            if epoch != self.epoch:
                continue
            self.busy = True
            try:
                if action in _SETUP_ACTIONS and self.pad and self.pad.down:
                    await asyncio.to_thread(self.pad.release)
                    if epoch != self.epoch:
                        continue
                if action in ('pair', 'confirm', 'reconnect'):
                    self.tv_established = False
                if action == 'pair':
                    self.tv_task = self.loop.create_task(self.tv.start_pairing())
                    await self.tv_task
                    self.status.set('Enter the code shown on the projector, then Confirm code.')
                elif action == 'confirm':
                    self.tv_task = self.loop.create_task(self.tv.finish_pairing(args[0]))
                    await self.tv_task
                    self.status.set('TV remote paired and connected.')
                elif action == 'reconnect':
                    self.tv_task = self.loop.create_task(self.tv.reconnect())
                    await self.tv_task
                    self.status.set('TV remote reconnected.')
                elif action == 'key':
                    self.tv.key(args[0])
                elif action == 'netflix':
                    self.tv.launch_netflix()
                    self.status.set('Netflix launch requested. Observe the projector to verify.')
                elif action == 'pointer':
                    self.pad_established = False
                    if self.pad:
                        await asyncio.to_thread(self.pad.close)
                        if epoch != self.epoch:
                            continue
                    pad = await asyncio.to_thread(pointer.start, self.serial)
                    if epoch != self.epoch:
                        await asyncio.to_thread(pad.close)
                        continue
                    self.pad = pad
                    await asyncio.to_thread(pad.move, 0, 0)
                    self.status.set('Touchpad connected. Confirm pointer visibility on the projector.')
                else:
                    if self.pad is None or not self.pad.connected:
                        raise ConnectionError('Touchpad disconnected')
                    await asyncio.to_thread(getattr(self.pad, action), *args)
                if epoch != self.epoch:
                    self.tv.disconnect()
                elif action in ('confirm', 'reconnect'):
                    self.tv_established = True
                elif action == 'pointer':
                    self.pad_established = True
            except asyncio.CancelledError:
                if self.closing:
                    raise
                self.status.set('Operation stopped. Reconnect before continuing.')
            except Exception as exc:
                self.stop()
                # Dependencies may put pairing codes or addresses in their errors.
                if action == 'pointer':
                    self.status.set('Touchpad connection failed; both channels stopped. '
                                    'Check the configured ADB address and that the projector is awake '
                                    'on the same Wi-Fi. Use Reconnect TV to restore TV controls.')
                else:
                    self.status.set(f'{action.capitalize()} failed ({type(exc).__name__}). Check connection or pair again.')
            finally:
                self.tv_task = None
                if action in _SETUP_ACTIONS and epoch == self.epoch:
                    self.setting_up = False
                self.busy = False

    def stop(self):
        self.epoch += 1
        self.tv_established = self.pad_established = False
        self.setting_up = False
        self.pending[:] = [0., 0.]
        while not self.queue.empty():
            self.queue.get_nowait()
        if self.tv_task:
            self.tv_task.cancel()
        self.tv.disconnect()
        if self.pad:
            pad, self.pad = self.pad, None
            pad.request_stop()
            self.loop.create_task(asyncio.to_thread(pad.close))
        self.status.set('Stopped. TV remote and touchpad are disconnected.')

    def refresh(self):
        if self.closing:
            return
        lost = None
        if self.tv_established and not self.tv.connected:
            lost = 'TV remote'
        elif self.pad_established and (self.pad is None or not self.pad.connected):
            lost = 'Touchpad'
        if lost:
            self.stop()
            self.status.set(f'{lost} connection lost. Both channels stopped.')
        tv = 'connected' if self.tv.connected else 'disconnected'
        pad = 'connected' if self.pad and self.pad.connected else 'disconnected'
        self.channel.set(f'TV remote: {tv} | Touchpad: {pad}')
        self.root.after(100, self.refresh)

    def shutdown(self):
        self.stop()
        self.closing = True
        self.root.withdraw()
        self.loop.create_task(self.finish())

    async def finish(self):
        # In-flight startup is allowed to unwind and dispose its owned server.
        while self.busy:
            await asyncio.sleep(.05)
        self.consumer.cancel()
        self.tv.close()
        await asyncio.sleep(.1)
        self.destroyed = True
        self.root.destroy()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Explicit authorized ADB device')
    parser.add_argument('--host', required=True, help='Projector private IPv4 address')
    args = parser.parse_args()
    logging.getLogger('androidtvremote2').disabled = True
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    root = tk.Tk()
    window = RemoteWindow(root, loop, args.serial, args.host)

    def pump():
        loop.call_soon(loop.stop)
        loop.run_forever()
        if not window.destroyed:
            root.after(10, pump)

    root.after(0, pump)
    try:
        root.mainloop()
    finally:
        window.tv.close()
        if window.pad:
            window.pad.close()
        tasks = asyncio.all_tasks(loop)
        for task in tasks:
            task.cancel()
        loop.run_until_complete(asyncio.gather(*tasks, return_exceptions=True))
        loop.run_until_complete(loop.shutdown_default_executor())
        loop.close()


if __name__ == '__main__':
    main()
