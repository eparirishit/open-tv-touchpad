import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {mountApp} from '../../web/remote.js';

class Target {
  constructor() { this.handlers = new Map(); this.disabled = false; this.hidden = false; this.value = ''; this.textContent = ''; this.dataset = {}; this.attributes = {}; }
  addEventListener(name, fn) { const list = this.handlers.get(name) || []; list.push(fn); this.handlers.set(name,list); }
  async emit(name, fields = {}) { for (const fn of this.handlers.get(name) || []) await fn({preventDefault(){}, ...fields}); }
  setAttribute(name,value) { this.attributes[name] = value; }
  setPointerCapture() {}
}
async function fixture(installed = true) {
  const html = await readFile(new URL('../../web/index.html',import.meta.url),'utf8');
  const nodes = new Map([...html.matchAll(/id="([^"]+)"/g)].map(m => [m[1],new Target()]));
  const keys = [...html.matchAll(/data-key="([^"]+)"/g)].map(m => Object.assign(new Target(),{dataset:{key:m[1]}}));
  const scrolls = [...html.matchAll(/data-scroll="([^"]+)"/g)].map(m => Object.assign(new Target(),{dataset:{scroll:m[1]}}));
  const document = Object.assign(new Target(),{visibilityState:'visible',getElementById:id => nodes.get(id),querySelectorAll:selector => selector === '[data-key]' ? keys : scrolls});
  const sockets = [], calls = []; let reloads = 0;
  const win = Object.assign(new Target(),{document,navigator:{onLine:true,standalone:false},performance:{now:() => 0},
    location:{origin:'https://192.168.1.2:8443',reload:() => reloads++},matchMedia:() => Object.assign(new Target(),{matches:installed}),
    setInterval:() => 1,clearInterval(){},setTimeout:() => 1,clearTimeout(){},
    fetch:async (path,options) => { calls.push({path,options}); return new Response(JSON.stringify({csrf:'fixture',session:{id:'id',label:'<b>Phone</b>',expires:9999999999},status:{available:false,connected:false}})); },
    WebSocket:class { constructor(url) { this.url=url; this.readyState=1; this.bufferedAmount=0; this.sent=[]; sockets.push(this); } send(data){this.sent.push(JSON.parse(data));} close(){this.readyState=3;} },
  });
  const app = mountApp(win); await app.ready;
  return {app,win,nodes,keys,scrolls,sockets,calls,reloads:() => reloads};
}

test('mounted page gates install/enrollment and unavailable controls; connects only on action', async () => {
  const f = await fixture(); assert.equal(f.calls.length,1); assert.equal(f.calls[0].path,'/api/session');
  assert.equal(f.sockets.length,0); assert.equal(f.nodes.get('controls').disabled,true);
  assert.match(f.nodes.get('session-label').textContent,/<b>Phone<\/b>/);
  await f.nodes.get('reconnect').emit('click'); const s = f.sockets[0]; s.onopen();
  s.onmessage({data:'{"version":1,"kind":"hello","status":{"available":false,"connected":false}}'});
  await f.nodes.get('take-control').emit('click'); s.onmessage({data:'{"kind":"ack","seq":1,"status":"admitted","lease":"owned"}'});
  assert.equal(f.nodes.get('controls').disabled,true); assert.equal(f.nodes.get('connect').disabled,true);
  await f.keys[0].emit('click'); await f.scrolls[0].emit('click'); assert.equal(s.sent.length,2);
  await f.nodes.get('stop').emit('click'); assert.equal(s.sent.at(-1).kind,'stop');
  f.win.navigator.onLine=false; await f.win.emit('offline'); assert.equal(f.nodes.get('reconnect').disabled,true);
  f.win.navigator.onLine=true; await f.win.emit('online'); assert.equal(f.sockets.length,1);
});

test('normal tab cannot enroll; hidden page cancels and update stops before reload', async () => {
  const f = await fixture(false); assert.equal(f.calls.length,0); assert.equal(f.nodes.get('enroll').disabled,true);
  f.nodes.get('label').value='Phone'; await f.nodes.get('enroll-form').emit('submit'); assert.equal(f.calls.length,0);
  await f.nodes.get('refresh').emit('click'); await f.nodes.get('reconnect').emit('click'); const s=f.sockets[0];
  s.onopen(); s.onmessage({data:'{"version":1,"kind":"hello","status":{"available":false,"connected":false}}'});
  f.win.document.visibilityState='hidden'; await f.win.document.emit('visibilitychange');
  assert.equal(s.sent.some(m => m.kind === 'stop'),false); assert.equal(f.app.remote.state.ready,false);
  await f.nodes.get('reload').emit('click'); assert.equal(f.reloads(),1);
});

test('capture failure and third pointer use owned cleanup, never global Stop', async () => {
  for (const cause of ['capture','third','cancel']) {
    const f=await fixture(); await f.nodes.get('reconnect').emit('click'); const s=f.sockets[0]; s.onopen();
    s.onmessage({data:'{"version":1,"kind":"hello","status":{"available":true,"connected":true}}'});
    await f.nodes.get('take-control').emit('click'); s.onmessage({data:'{"kind":"ack","seq":1,"status":"admitted","lease":"owned"}'});
    const pad=f.nodes.get('touchpad');
    if(cause==='capture') pad.setPointerCapture=() => {throw new Error('capture lost');};
    await pad.emit('pointerdown',{pointerId:1,pointerType:'touch',clientX:0,clientY:0});
    if(cause==='third') {
      await pad.emit('pointerdown',{pointerId:2,pointerType:'touch',clientX:0,clientY:0});
      await pad.emit('pointerdown',{pointerId:3,pointerType:'touch',clientX:0,clientY:0});
    } else if(cause==='cancel') await pad.emit('pointercancel');
    assert.equal(s.sent.at(-1).kind,'release_control'); assert.equal(s.sent.some(m=>m.kind==='stop'),false);
    assert.equal(f.app.remote.state.ready,false);
  }
});
