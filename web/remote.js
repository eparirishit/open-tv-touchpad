import { createGestures } from './gestures.js';

const VERSION = 1, MAX_BYTES = 4096;
const KEYS = new Set(['UP', 'DOWN', 'LEFT', 'RIGHT', 'OK', 'HOME', 'BACK', 'VOLUME_UP', 'VOLUME_DOWN', 'PLAY_PAUSE']);
const INPUTS = new Set(['key', 'netflix', 'move', 'press', 'release', 'tap', 'scroll']);
const EMPTY = new Set(['take_control', 'release_control', 'stop', 'connect', 'netflix', 'press', 'release', 'tap', 'heartbeat']);
const bytes = value => new TextEncoder().encode(value).byteLength;
const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max;
const statusOK = s => s && typeof s.available === 'boolean' && typeof s.connected === 'boolean' && (!s.connected || s.available);
const clamp = n => Math.max(-256, Math.min(256, n));

function validCommand(kind, fields) {
  if (!fields || typeof fields !== 'object' || Array.isArray(fields)) return false;
  const names = Object.keys(fields);
  if (EMPTY.has(kind)) return names.length === 0;
  if (kind === 'key') return names.length === 1 && KEYS.has(fields.key);
  const limit = kind === 'move' ? 256 : kind === 'scroll' ? 1 : 0;
  return limit > 0 && names.length === 2 && names.includes('dx') && names.includes('dy') &&
    [fields.dx, fields.dy].every(n => Number.isInteger(n) && Math.abs(n) <= limit);
}

export async function readJSON(response) {
  if (!response.body) throw new Error('Missing response');
  const reader = response.body.getReader(), decoder = new TextDecoder('utf-8', {fatal:true});
  let size = 0, result = '';
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BYTES) throw new Error('Response too large');
      result += decoder.decode(value, {stream:true});
    }
    result += decoder.decode();
    const parsed = JSON.parse(result);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Invalid response');
    return parsed;
  } finally { await reader.cancel(); reader.releaseLock(); }
}

async function request(fetcher, path, body, csrf) {
  const response = await fetcher(path, {method: body === undefined ? 'GET' : 'POST', credentials:'same-origin',
    cache:'no-store', redirect:'error', signal:AbortSignal.timeout(5000),
    headers: body === undefined ? {} : {'Content-Type':'application/json', ...(csrf ? {'X-CSRF-Token':csrf} : {})},
    ...(body === undefined ? {} : {body: JSON.stringify(body)})});
  if (!response.ok) throw new Error(response.status === 401 ? 'Enrollment required' : 'Hub request refused');
  return {data: await readJSON(response), status:response.status};
}

export async function enroll(fetcher, label, installed) {
  if (!installed) throw new Error('Install and open the app before enrollment');
  if (!text(label, 32) || !label.trim() || /[\u0000-\u001f\u007f]/u.test(label)) throw new Error('Use a label of 1–32 characters');
  const {data} = await request(fetcher, '/api/enroll/request', {label});
  if (!text(data.csrf, 256) || typeof data.code !== 'string' || !/^\d{6}$/.test(data.code) || !Number.isFinite(data.expires) || data.expires <= 0 || data.expires > 120) throw new Error('Invalid enrollment response');
  return data;
}

export class SessionFlow {
  constructor({fetcher = (...args) => fetch(...args), now = () => performance.now(), onSession = () => {}, onChange = () => {}} = {}) {
    this.fetcher = fetcher; this.now = now; this.onSession = onSession; this.onChange = onChange;
    this.pending = null; this.generation = 0; this.running = false;
    this.state = {busy:false, code:'', message:'Install the app, then request enrollment.'};
  }
  changed() { this.onChange({...this.state}); }
  cancel() {
    this.generation++; this.pending = null; this.state.code = '';
    this.state.message = 'Request discarded. The Mac owner can cancel the old request.'; this.changed();
  }
  async run(action) {
    if (this.running) return;
    this.running = this.state.busy = true; this.changed();
    const generation = this.generation;
    const current = () => generation === this.generation;
    try { await action(current); }
    catch (error) {
      if (current()) {
        this.pending = null; this.state.code = '';
        this.state.message = error.message === 'Enrollment required' ? 'Enrollment required.' : 'Request failed. Refresh session; if approval was lost, ask the Mac owner to revoke it and enroll again.';
      }
    } finally { this.running = this.state.busy = false; this.changed(); }
  }
  async refresh() {
    if (this.running) return;
    this.cancel(); this.onSession(null);
    return this.run(async current => {
      const {data} = await request(this.fetcher, '/api/session');
      if (current()) { this.onSession(data); this.state.message = 'Session refreshed. Reconnect the session socket when ready.'; }
    });
  }
  async enroll(label, installed) {
    if (this.running || this.pending) return;
    return this.run(async current => {
      const started = this.now();
      const data = await enroll(this.fetcher,label,installed);
      if (!current()) return;
      this.pending = {csrf:data.csrf, deadline:started + data.expires * 1000, next:this.now() + 1000};
      this.state.code = data.code; this.state.message = 'Compare this code with the native Mac window; the owner approves there.';
    });
  }
  async poll() {
    const pending = this.pending;
    if (!pending || this.running) return;
    if (this.now() >= pending.deadline) { this.cancel(); this.state.message = 'Enrollment expired. Ask the Mac owner to open a new window.'; this.changed(); return; }
    if (this.now() < pending.next) return;
    pending.next = this.now() + 1000;
    return this.run(async current => {
      const result = await request(this.fetcher, '/api/enroll/complete', {}, pending.csrf);
      if (!current()) return;
      if (result.status === 202) return;
      if (result.status !== 200) throw new Error('Invalid completion status');
      this.pending = null; this.state.code = '';
      const {data} = await request(this.fetcher, '/api/session');
      if (current()) { this.onSession(data); this.state.message = 'Enrolled. Reconnect the session socket to continue.'; }
    });
  }
  async logout(csrf) {
    if (this.running || !text(csrf,256)) return;
    this.cancel();
    return this.run(async current => {
      await request(this.fetcher, '/api/logout', {}, csrf);
      if (current()) { this.onSession(null); this.state.message = 'Signed out; this controller enrollment was revoked.'; }
    });
  }
}

export class Remote {
  constructor({origin, socketFactory = url => new WebSocket(url), now = () => performance.now(), onChange = () => {}, onCancel = () => {}, reload = () => location.reload()}) {
    const url = new URL(origin);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('HTTPS required');
    this.url = `wss://${url.host}/ws`; this.socketFactory = socketFactory; this.now = now;
    this.onChange = onChange; this.onCancel = onCancel; this.reload = reload;
    this.socket = null; this.csrf = null; this.seq = 0; this.pending = new Map(); this.movement = null;
    this.lastTick = now(); this.lastBeat = now(); this.lastMove = now(); this.tokens = 120; this.tokenAt = now();
    this.state = {session:null, online:true, visible:true, ready:false, available:false, connected:false, lease:null, needsReload:false, canControl:false, message:'Refresh session to begin'};
  }
  changed() {
    this.state.canControl = Boolean(this.state.ready && this.state.online && this.state.visible && !this.state.needsReload && this.state.available && this.state.connected && this.state.lease);
    this.onChange({...this.state});
  }
  setSession(data) {
    if (!data || !text(data.csrf,256) || !data.session || !text(data.session.id,256) || !text(data.session.label,32) || !Number.isFinite(data.session.expires) || !statusOK(data.status)) throw new Error('Invalid session response');
    this.disconnect('Session refreshed; reconnect explicitly');
    this.csrf = data.csrf; this.state.session = data.session;
    this.state.available = data.status.available; this.state.connected = data.status.connected; this.changed();
  }
  clearSession() {
    this.disconnect('Enrollment required'); this.csrf = null; this.state.session = null; this.changed();
  }
  reconnect() {
    if (!this.csrf || !this.state.online || !this.state.visible || this.state.needsReload) return false;
    this.disconnect('Opening session socket'); this.seq = 0; this.tokens = 120; this.tokenAt = this.now();
    const s = this.socketFactory(this.url); this.socket = s; this.openedAt = this.now();
    s.onopen = () => { if (this.socket === s) this.write({version:VERSION, kind:'hello', csrf:this.csrf}); };
    s.onmessage = event => { if (this.socket === s) this.receive(event.data); };
    s.onclose = s.onerror = () => { if (this.socket === s) this.disconnect('Socket lost; reconnect explicitly'); };
    return true;
  }
  write(message) {
    const encoded = JSON.stringify(message), s = this.socket;
    if (!s || s.readyState !== 1) return false;
    if (bytes(encoded) > MAX_BYTES || s.bufferedAmount + bytes(encoded) > 32768) { this.disconnect('Output overflow; reconnect explicitly'); return false; }
    try { s.send(encoded); return true; } catch { this.disconnect('Socket write failed'); return false; }
  }
  send(kind, fields = {}) {
    const now = this.now();
    this.tokens = Math.min(120, this.tokens + (now - this.tokenAt) * 0.05); this.tokenAt = now;
    if (kind !== 'stop' && (this.pending.size >= 8 || this.tokens < 1)) { this.disconnect('Input limit reached; reconnect explicitly'); return false; }
    const seq = this.seq + 1;
    if (!Number.isSafeInteger(seq)) { this.disconnect('Sequence exhausted'); return false; }
    const message = {version:VERSION, seq, kind, ...fields};
    if (!['take_control','stop'].includes(kind)) message.lease = this.state.lease;
    if (!this.write(message)) return false;
    this.seq = seq; this.tokens--;
    this.pending.set(seq, {kind, at:now}); return true;
  }
  command(kind, fields = {}) {
    if (!validCommand(kind,fields)) return false;
    if (kind === 'stop') { this.stop(); return true; }
    if (!this.state.ready || !this.state.online || !this.state.visible || this.state.needsReload) return false;
    if (INPUTS.has(kind) && !this.state.canControl) return false;
    if (this.now() - this.lastTick >= 3000) { this.cancelControl('Session suspended; reconnect explicitly'); return false; }
    if (kind === 'take_control') {
      if (this.state.lease || [...this.pending.values()].some(p => p.kind === kind)) return false;
    } else if (!this.state.lease) return false;
    if (kind === 'connect' && !this.state.available) return false;
    if (kind === 'move') {
      this.movement = {dx:clamp((this.movement?.dx || 0) + fields.dx), dy:clamp((this.movement?.dy || 0) + fields.dy)}; return true;
    }
    if (kind === 'release_control') this.onCancel();
    // Keep movement ordered before a button transition; never save input for reconnect.
    if (['press','release','tap','scroll'].includes(kind)) this.flushMove();
    const sent = this.send(kind,fields);
    if (kind === 'release_control') { this.state.lease = null; this.movement = null; this.changed(); }
    return sent;
  }
  flushMove() {
    const movement = this.movement; this.movement = null;
    if (movement && this.state.canControl) { this.lastMove = this.now(); this.send('move',movement); }
  }
  stop() {
    this.movement = null;
    const ready = this.state.ready;
    this.state.canControl = false; this.state.lease = null; this.pending.clear(); this.onCancel();
    if (ready) this.send('stop');
    this.state.message = 'Stopped'; this.changed();
  }
  disconnect(message) {
    const socket = this.socket; this.socket = null;
    this.state.ready = false; this.state.lease = null; this.state.connected = false; this.state.canControl = false;
    this.pending.clear(); this.movement = null; this.onCancel();
    if (socket) socket.close();
    this.state.message = message; this.changed();
  }
  cancelControl(message) {
    this.movement = null; this.state.canControl = false; this.pending.clear(); this.onCancel();
    // Automatic cleanup must not stop a successor or another tab's owner.
    if (this.state.ready && this.state.lease) this.send('release_control');
    this.disconnect(message);
  }
  receive(raw) {
    try {
      if (typeof raw !== 'string' || raw.length > MAX_BYTES || bytes(raw) > MAX_BYTES) throw new Error();
      const data = JSON.parse(raw);
      if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error();
      if (data.version !== undefined && data.version !== VERSION) {
        this.stop(); this.state.needsReload = true; this.disconnect('Protocol changed. Reload to update.'); return;
      }
      if (!this.state.ready) {
        if (data.kind !== 'hello' || data.version !== VERSION || !statusOK(data.status)) throw new Error();
        this.state.ready = true; this.state.available = data.status.available; this.state.connected = data.status.connected;
        this.lastTick = this.lastBeat = this.now(); this.state.message = 'Session socket ready. Take control explicitly.';
      } else if (data.kind === 'status') {
        if (!statusOK(data) || (data.lease != null && !text(data.lease,256))) throw new Error();
        this.state.available = data.available; this.state.connected = data.connected;
        if (Object.hasOwn(data,'lease') && data.lease !== this.state.lease) {
          this.state.lease = null; this.movement = null; this.state.canControl = false; this.onCancel();
        }
        if (!data.connected) { this.movement = null; this.state.canControl = false; this.onCancel(); }
      } else if (data.kind === 'ack') {
        if (!Number.isSafeInteger(data.seq) || data.seq <= 0 || !['admitted','dispatched','failed'].includes(data.status) || (data.error !== undefined && !text(data.error,256))) throw new Error();
        const pending = this.pending.get(data.seq);
        if (!pending) return;
        this.pending.delete(data.seq);
        if (data.status === 'failed' && data.error === 'version') {
          this.stop(); this.state.needsReload = true; this.disconnect('Protocol changed. Reload to update.'); return;
        }
        if (pending.kind === 'take_control' && data.status === 'admitted') {
          if (!text(data.lease,256)) throw new Error();
          this.state.lease = data.lease; this.lastBeat = this.now(); this.state.message = 'Control lease held';
        } else if (data.status === 'failed') {
          if (pending.kind !== 'take_control') this.disconnect('Command failed; reconnect explicitly');
          this.state.message = data.error === 'busy' ? 'Control is busy. Ask the owner to release it.' : 'Command failed; check the Mac hub.';
        } else this.state.message = data.status === 'dispatched' ? 'Sent to device channel; on-screen result unverified.' : 'Command admitted by hub.';
      } else throw new Error();
      this.changed();
    } catch { this.disconnect('Invalid hub message; reconnect explicitly'); }
  }
  tick() {
    const now = this.now(), gap = now - this.lastTick; this.lastTick = now;
    if (this.socket && !this.state.ready && now - this.openedAt >= 3000) { this.disconnect('Session hello timed out'); return; }
    if (!this.state.ready) return;
    if (gap >= 3000 || [...this.pending.values()].some(p => now - p.at >= (p.kind === 'connect' ? 12000 : 3000))) {
      this.cancelControl('Session suspended or acknowledgement missed; reconnect explicitly'); return;
    }
    if (this.state.lease && now - this.lastBeat >= 1000) { this.lastBeat = now; this.command('heartbeat'); }
    if (now - this.lastMove >= 1000 / 30) this.flushMove();
  }
  setOnline(value) { if (!value) this.cancelControl('Offline. Controls disabled.'); this.state.online = value; this.changed(); }
  setVisible(value) { if (!value) this.cancelControl('Backgrounded. Reconnect explicitly.'); this.state.visible = value; this.changed(); }
  canActivateUpdate() { return !this.state.lease && !this.movement && ![...this.pending.values()].some(p => p.kind === 'take_control'); }
  reloadToUpdate() { this.stop(); this.disconnect('Reloading'); this.reload(); }
}

export function mountApp(win = window) {
  const doc = win.document, nav = win.navigator, byId = id => doc.getElementById(id);
  const mode = win.matchMedia('(display-mode: standalone)');
  const installed = () => mode.matches || nav.standalone === true;
  let remote, flow, gestures, installPrompt, registration, updating = false;
  const captured = new Set();
  function render() {
    if (!remote || !flow) return;
    const s = remote.state, f = flow.state;
    byId('status').textContent = s.message;
    byId('session-label').textContent = s.session ? `Enrolled: ${s.session.label}` : 'No enrolled session loaded';
    byId('backend-status').textContent = !s.online ? 'Offline · controls disabled' : !s.available ? 'TV backend unavailable' : s.connected ? 'Device channel connected' : 'Device channel disconnected';
    byId('controls').disabled = !s.canControl || updating;
    byId('touchpad').setAttribute('aria-disabled', String(!s.canControl || updating));
    byId('touchpad').tabIndex = s.canControl && !updating ? 0 : -1;
    byId('refresh').disabled = !s.online || f.busy || updating;
    byId('reconnect').disabled = !s.session || !s.online || !s.visible || s.needsReload || updating;
    byId('logout').disabled = !s.session || !s.online || f.busy || updating;
    byId('take-control').disabled = !s.ready || Boolean(s.lease) || s.needsReload || updating || [...remote.pending.values()].some(p => p.kind === 'take_control');
    byId('release-control').disabled = !s.ready || !s.lease || updating;
    byId('connect').disabled = !s.ready || !s.lease || !s.available || s.connected || updating;
    byId('stop').disabled = !s.ready;
    byId('reload').hidden = !s.needsReload;
    byId('enroll').disabled = !installed() || !s.online || !s.visible || Boolean(s.session) || f.busy || Boolean(flow.pending) || updating;
    byId('label').disabled = byId('enroll').disabled;
    byId('enroll-status').textContent = f.message;
    byId('comparison').hidden = !f.code; byId('code').textContent = f.code;
    byId('cancel-enrollment').hidden = !flow.pending;
    byId('install-state').textContent = installed() ? 'Installed app context. Enrollment is available when online.' : 'Open the installed app to enroll; this tab is a separate context.';
    byId('install').hidden = !installPrompt || installed();
    byId('update').hidden = !registration?.waiting;
    byId('update').disabled = updating;
  }
  remote = new Remote({origin:win.location.origin, socketFactory:url => new win.WebSocket(url), now:() => win.performance.now(),
    onChange:render, onCancel:() => { gestures?.reset(); captured.clear(); }, reload:() => win.location.reload()});
  gestures = createGestures((kind,fields) => remote.command(kind,fields), {now:() => win.performance.now(),
    setTimeout:(fn,ms) => win.setTimeout(fn,ms), clearTimeout:id => win.clearTimeout(id)});
  flow = new SessionFlow({fetcher:(...args) => win.fetch(...args), now:() => win.performance.now(), onChange:render,
    onSession:data => data ? remote.setSession(data) : remote.clearSession()});
  const on = (id,event,fn) => byId(id).addEventListener(event,fn);
  on('enroll-form','submit',event => {
    event.preventDefault();
    if (byId('enroll').disabled) return;
    return flow.enroll(byId('label').value.trim(),installed());
  });
  on('cancel-enrollment','click',() => flow.cancel());
  on('refresh','click',() => { remote.cancelControl('Refreshing session'); return flow.refresh(); });
  on('reconnect','click',() => { if (!updating) remote.reconnect(); });
  on('logout','click',() => { const csrf = remote.csrf; remote.cancelControl('Signing out'); return flow.logout(csrf); });
  for (const [id,kind] of [['take-control','take_control'],['release-control','release_control'],['connect','connect']]) {
    on(id,'click',() => { if (!updating) { remote.command(kind); render(); } });
  }
  on('stop','click',() => remote.stop());
  for (const button of doc.querySelectorAll('[data-key]')) button.addEventListener('click',() => { if (!updating) remote.command('key',{key:button.dataset.key}); });
  for (const button of doc.querySelectorAll('[data-scroll]')) button.addEventListener('click',() => {
    const [dx,dy] = button.dataset.scroll.split(',').map(Number);
    if (!updating) remote.command('scroll',{dx,dy});
  });
  on('netflix','click',() => { if (!updating) remote.command('netflix'); });
  on('sensitivity','input',() => {
    const value = Number(byId('sensitivity').value);
    if (Number.isFinite(value) && value >= 0.25 && value <= 4) { gestures.setSensitivity(value); byId('sensitivity-value').textContent = `${value}×`; }
  });
  const pad = byId('touchpad');
  on('touchpad','pointerdown',event => {
    if (!remote.state.canControl || updating) return;
    event.preventDefault();
    if (captured.size >= 2) { remote.cancelControl('Too many pointers; reconnect explicitly'); return; }
    try { pad.setPointerCapture(event.pointerId); captured.add(event.pointerId); gestures.pointerdown(event); }
    catch { remote.cancelControl('Pointer capture lost; reconnect explicitly'); }
  });
  on('touchpad','pointermove',event => { if (remote.state.canControl && !updating) { event.preventDefault(); gestures.pointermove(event); } });
  on('touchpad','pointerup',event => { captured.delete(event.pointerId); gestures.pointerup(event); });
  const cancelInput = () => remote.cancelControl('Input cancelled; reconnect explicitly');
  on('touchpad','pointercancel',cancelInput);
  on('touchpad','lostpointercapture',event => { if (captured.has(event.pointerId)) cancelInput(); });
  on('touchpad','pointerleave',event => { if (event.pointerType === 'mouse') gestures.reset(); });
  on('touchpad','contextmenu',event => event.preventDefault());
  on('touchpad','keydown',event => {
    if (!remote.state.canControl || updating) return;
    const deltas = {ArrowUp:[0,-16],ArrowDown:[0,16],ArrowLeft:[-16,0],ArrowRight:[16,0]};
    if (deltas[event.key]) { event.preventDefault(); const [dx,dy] = deltas[event.key]; remote.command('move',{dx,dy}); }
    else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (!event.repeat) remote.command('tap'); }
    else if (event.key === 'Escape') { event.preventDefault(); cancelInput(); }
  });
  win.addEventListener('offline',() => { flow.cancel(); remote.setOnline(false); });
  win.addEventListener('online',() => remote.setOnline(true));
  doc.addEventListener('visibilitychange',() => {
    const visible = doc.visibilityState === 'visible';
    if (!visible) flow.cancel(); remote.setVisible(visible);
  });
  win.addEventListener('pagehide',() => { flow.cancel(); remote.setVisible(false); });
  win.addEventListener('pageshow',() => { remote.setOnline(nav.onLine); remote.setVisible(doc.visibilityState === 'visible'); });
  win.addEventListener('beforeinstallprompt',event => { event.preventDefault(); installPrompt = event; render(); });
  win.addEventListener('appinstalled',() => { installPrompt = null; render(); });
  mode.addEventListener('change',render);
  on('install','click',async () => {
    const prompt = installPrompt; installPrompt = null; render();
    if (prompt) { try { await prompt.prompt(); } catch { byId('install-state').textContent = 'Use your browser’s installation menu.'; } }
  });
  const update = () => {
    remote.stop(); remote.disconnect('Stopped for app update'); flow.cancel();
    if (!remote.canActivateUpdate() || gestures.active) return;
    updating = true; render();
    if (registration?.waiting) {
      byId('update-status').textContent = 'Close all other hub app windows, then reopen this app if the update waits. Controls are stopped.';
      registration.waiting.postMessage({kind:'activate',inactive:true});
    } else remote.reloadToUpdate();
  };
  on('update','click',update); on('reload','click',update);
  if (nav.serviceWorker) {
    nav.serviceWorker.addEventListener('controllerchange',() => remote.reloadToUpdate());
    nav.serviceWorker.register('/sw.js',{scope:'/',updateViaCache:'none'}).then(reg => {
      registration = reg; render();
      const watch = () => {
        const worker = reg.installing;
        worker?.addEventListener('statechange',() => { if (worker.state === 'installed') { byId('update-status').textContent = 'App update ready. Stop and update when convenient.'; render(); } });
      };
      reg.addEventListener('updatefound',watch); watch();
    }).catch(() => { byId('update-status').textContent = 'Offline installation unavailable. Reopen online after certificate trust is verified.'; });
  }
  remote.setOnline(nav.onLine); remote.setVisible(doc.visibilityState === 'visible'); render();
  const timer = win.setInterval(() => { remote.tick(); if (remote.state.online && remote.state.visible) void flow.poll(); },34);
  const ready = installed() && nav.onLine && remote.state.visible ? flow.refresh() : Promise.resolve();
  return {remote,flow,gestures,ready,destroy:() => { win.clearInterval(timer); flow.cancel(); remote.cancelControl('Closed'); }};
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  try { mountApp(); }
  catch { document.getElementById('status').textContent = 'Open the trusted HTTPS hub address in a supported browser. Controls are disabled.'; }
}
