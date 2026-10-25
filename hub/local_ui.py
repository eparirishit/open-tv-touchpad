"""Local owner controls. No administration route exists in the browser."""
import tkinter as tk
from tkinter import filedialog, ttk


class OwnerWindow:
    def __init__(self, root, bridge, address, worker):
        self.root, self.bridge, self.worker = root, bridge, worker
        self.closing = False
        self.pending, self.sessions = [], []
        self.rows = None
        root.title('Open TV Touchpad — local browser setup')
        root.geometry('760x640')
        root.protocol('WM_DELETE_WINDOW', self.close)
        frame = ttk.Frame(root, padding=16)
        frame.pack(fill='both', expand=True)
        ttk.Label(frame, text='Browser-only setup · TV integration locked',font=('',16,'bold')).pack(anchor='w')
        ttk.Label(frame, text=f'Local origin: https://{address}:8443').pack(anchor='w', pady=8)
        ttk.Label(frame, text='Create once → export and verify root → trust on device → install PWA → enroll.\n'
                  'Certificate trust also applies to this IP on other networks. Remove it when retiring this hub.',
                  wraplength=720).pack(anchor='w')
        actions=ttk.Frame(frame); actions.pack(fill='x',pady=10)
        for label, action in [('Create identity','create'),('Start HTTPS','start'),('Stop HTTPS','stop_server'),('Open enrollment','open_window')]:
            ttk.Button(actions,text=label,command=lambda a=action:self.send(a)).pack(side='left',padx=3)
        ttk.Button(frame,text='Export public root certificate',command=self.export).pack(anchor='w')
        self.fingerprint=tk.StringVar(value='Export to display the full SHA-256 fingerprint.')
        ttk.Entry(frame,textvariable=self.fingerprint,state='readonly',width=90).pack(fill='x',pady=8)
        ttk.Label(frame,text='Pending requests — compare the code on the installed PWA before approving').pack(anchor='w')
        self.pending_list=tk.Listbox(frame,height=4,exportselection=False)
        self.pending_list.pack(fill='x')
        row=ttk.Frame(frame);row.pack(fill='x',pady=5)
        ttk.Button(row,text='Approve selected code',command=lambda:self.selected('approve',self.pending_list,self.pending)).pack(side='left')
        ttk.Button(row,text='Cancel selected',command=lambda:self.selected('cancel',self.pending_list,self.pending)).pack(side='left',padx=5)
        ttk.Label(frame,text='Enrolled controllers').pack(anchor='w')
        self.session_list=tk.Listbox(frame,height=4,exportselection=False);self.session_list.pack(fill='x')
        row=ttk.Frame(frame);row.pack(fill='x',pady=5)
        ttk.Button(row,text='Revoke selected',command=lambda:self.selected('revoke',self.session_list,self.sessions)).pack(side='left')
        ttk.Button(row,text='Revoke all',command=lambda:self.send('revoke_all')).pack(side='left',padx=5)
        ttk.Button(frame,text='STOP CONTROL',command=bridge.stop).pack(anchor='w',pady=8)
        self.status=tk.StringVar(value='Starting owner controls…')
        ttk.Label(frame,textvariable=self.status,wraplength=720).pack(anchor='w')
        root.after(100,self.refresh)

    def send(self, action, value=None):
        if not self.bridge.submit(action,value):
            self.status.set('Owner queue is full. Wait briefly; Stop remains available.')

    def selected(self, action, widget, records):
        choice=widget.curselection()
        if choice:
            self.send(action,records[choice[0]]['pending_id' if action in ('approve','cancel') else 'id'])

    def export(self):
        destination=filedialog.asksaveasfilename(title='Export public root certificate',defaultextension='.cer',
                                               filetypes=[('Certificate','*.cer')])
        if destination:
            self.send('export',destination)

    def refresh(self):
        snapshot=self.bridge.latest()
        if snapshot:
            self.status.set(snapshot['message'])
            if snapshot['fingerprint']:
                self.fingerprint.set(snapshot['fingerprint'])
            rows=(snapshot['pending'],snapshot['sessions'])
            if rows!=self.rows:
                self.rows=rows
                self.pending,self.sessions=rows
                self.pending_list.delete(0,'end')
                for record in self.pending:
                    self.pending_list.insert('end',f"{record['label']} — {record['code']}")
                self.session_list.delete(0,'end')
                for record in self.sessions:
                    self.session_list.insert('end',record['label'])
        if self.closing and not self.worker.is_alive():
            self.root.destroy()
            return
        self.root.after(100,self.refresh)

    def close(self):
        self.closing=True
        self.bridge.stop()
        self.bridge.shutdown_requested.set()
        self.status.set('Closing HTTPS and controller sessions…')
