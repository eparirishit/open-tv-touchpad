import test from 'node:test';
import assert from 'node:assert/strict';
import { Remote, readJSON, enroll, SessionFlow } from '../../web/remote.js';

function fixture(available = true) {
  let time = 0, cancelled = 0, reloaded = 0;
  const sockets = [];
  const remote = new Remote({origin:'https://192.168.1.2:8443', now:() => time,
    socketFactory: url => { const s = {url, readyState:1, bufferedAmount:0, sent:[], send(data) { this.sent.push(JSON.parse(data)); }, close() { this.readyState = 3; }}; sockets.push(s); return s; },
    onCancel: () => cancelled++, reload: () => reloaded++});
  remote.setSession({csrf:'fixture-csrf', session:{id:'fixture-id', label:'Phone', expires:9999999999}, status:{available, connected:available}});
  remote.reconnect(); const s = sockets[0]; s.onopen();
  const reply = data => s.onmessage({data:JSON.stringify(data)});
  reply({version:1, kind:'hello', status:{available, connected:available}});
  const own = () => { remote.command('take_control'); reply({kind:'ack', seq:s.sent.at(-1).seq, status:'admitted', lease:'owned'}); };
  return {remote, sockets, s, reply, own, elapse:ms => {time += ms;}, advance:ms => {time += ms; remote.tick();}, cancelled:() => cancelled, reloaded:() => reloaded};
}

test('WSS hello authenticates once; only own take_control ack supplies lease', () => {
  const f = fixture(); assert.equal(f.s.url, 'wss://192.168.1.2:8443/ws');
  assert.deepEqual(f.s.sent[0], {version:1, kind:'hello', csrf:'fixture-csrf'});
  f.reply({kind:'status', available:true, connected:true, lease:'other'}); assert.equal(f.remote.state.lease, null);
  f.own(); assert.equal(f.remote.state.lease, 'owned');
  f.remote.command('key', {key:'HOME'});
  assert.deepEqual(f.s.sent.at(-1), {version:1, seq:2, kind:'key', key:'HOME', lease:'owned'});
});

test('confirmed ack envelope accepts owned lease and version error requests reload', () => {
  const f = fixture(); f.remote.command('take_control');
  f.reply({kind:'ack',seq:1,status:'admitted',lease:'owned'}); assert.equal(f.remote.state.lease,'owned');
  f.remote.command('tap'); f.reply({kind:'ack',seq:2,status:'failed',error:'version'});
  assert.equal(f.remote.state.needsReload,true); assert.equal(f.remote.state.canControl,false);
});

test('unavailable backend permits ownership but never connect or real inputs', () => {
  const f = fixture(false); f.own(); assert.equal(f.remote.state.canControl, false);
  for (const [kind, fields] of [['connect',{}], ['key',{key:'HOME'}], ['tap',{}], ['move',{dx:1,dy:1}]]) assert.equal(f.remote.command(kind, fields), false);
  assert.equal(f.s.sent.length, 2);
});

test('busy ack cannot seize control; observer Stop is allowed', () => {
  const f = fixture(); f.remote.command('take_control');
  f.reply({kind:'ack', seq:1, status:'failed', error:'busy'});
  assert.equal(f.remote.state.lease, null); assert.match(f.remote.state.message, /busy/i);
  f.remote.stop(); assert.equal(f.s.sent.at(-1).kind, 'stop');
});

test('heartbeat is once per second only for owner; missed timer fences suspend', () => {
  const f = fixture(); f.advance(1000); assert.equal(f.s.sent.length, 1);
  f.own(); f.advance(999); assert.equal(f.s.sent.at(-1).kind, 'take_control');
  f.advance(1); assert.equal(f.s.sent.at(-1).kind, 'heartbeat');
  f.advance(3001); assert.equal(f.remote.state.lease, null); assert.equal(f.remote.state.ready, false);
  assert.ok(f.cancelled());
});

test('hidden/offline/socket loss cancels, drops coalesced movement, and never auto reconnects', () => {
  for (const cause of ['hidden', 'offline', 'close']) {
    const f = fixture(); f.own(); f.remote.command('move', {dx:1,dy:2});
    if (cause === 'hidden') f.remote.setVisible(false);
    else if (cause === 'offline') f.remote.setOnline(false);
    else f.s.onclose();
    f.advance(1000); assert.equal(f.remote.state.canControl, false); assert.equal(f.remote.state.lease, null);
    assert.equal(f.s.sent.some(m => m.kind === 'move'), false);
    f.remote.setOnline(true); f.remote.setVisible(true); assert.equal(f.sockets.length, 1);
    assert.ok(f.cancelled());
  }
});

test('fresh socket starts seq at one and ignores late old ack', () => {
  const f = fixture(); f.remote.command('take_control'); f.s.onclose(); f.remote.reconnect();
  f.reply({kind:'ack',seq:1,status:'admitted',lease:'stale'}); assert.equal(f.remote.state.lease, null);
  const s = f.sockets[1]; s.onopen(); s.onmessage({data:JSON.stringify({version:1,kind:'hello',status:{available:true,connected:false}})});
  f.remote.command('take_control'); assert.equal(s.sent.at(-1).seq, 1);
});

test('version mismatch stops before reload and refuses active worker activation', () => {
  const f = fixture(); f.own(); assert.equal(f.remote.canActivateUpdate(), false);
  f.reply({version:2,kind:'status',available:true,connected:true});
  assert.equal(f.s.sent.at(-1).kind, 'stop'); assert.equal(f.remote.state.needsReload, true);
  assert.equal(f.remote.command('tap'), false); f.remote.reloadToUpdate(); assert.equal(f.reloaded(), 1);
});

test('status cannot replace owned lease; late take ack after Stop cannot revive it', () => {
  const f = fixture(); f.own(); f.reply({kind:'status',available:true,connected:true,lease:'other'});
  assert.equal(f.remote.state.lease, null); assert.equal(f.remote.state.canControl, false);
  f.remote.command('take_control'); const seq = f.s.sent.at(-1).seq; f.remote.stop();
  f.reply({kind:'ack',seq,status:'admitted',lease:'late'}); assert.equal(f.remote.state.lease, null);
});

test('movement is coalesced at 30Hz and clamped; invalid fields never sent', () => {
  const f = fixture(); f.own();
  f.remote.command('move',{dx:200,dy:-200}); f.remote.command('move',{dx:200,dy:-200});
  f.advance(34); assert.deepEqual(f.s.sent.at(-1), {version:1, seq:2, kind:'move', dx:256, dy:-256, lease:'owned'});
  const n = f.s.sent.length;
  for (const [kind,fields] of [['key',{key:'SHELL'}],['move',{dx:NaN,dy:1}],['scroll',{dx:2,dy:0}],['tap',{url:'x'}],['hello',{}]]) assert.equal(f.remote.command(kind,fields),false);
  assert.equal(f.s.sent.length,n);
});

test('oversize, binary, malformed and backpressured sockets fail closed', () => {
  for (const data of [' '.repeat(4097), new Uint8Array(1), '{', '{"kind":"status","available":1,"connected":true}']) {
    const f = fixture(); f.own(); f.s.onmessage({data}); assert.equal(f.remote.state.ready,false); assert.equal(f.remote.state.lease,null);
  }
  const f = fixture(); f.own(); f.s.bufferedAmount = 32768; f.remote.command('tap'); assert.equal(f.remote.state.ready,false);
});

test('HTTP JSON reader rejects >4KiB bytes and enrollment requires install and bounded label', async () => {
  await assert.rejects(readJSON(new Response('"' + 'x'.repeat(4096) + '"')));
  await assert.rejects(readJSON(new Response('"' + 'é'.repeat(2500) + '"')));
  let called = 0;
  const fetcher = async () => { called++; return new Response(JSON.stringify({csrf:'pending',code:'123456',expires:120})); };
  await assert.rejects(enroll(fetcher, 'Phone', false)); await assert.rejects(enroll(fetcher,'x'.repeat(33),true));
  assert.equal(called,0); const result = await enroll(fetcher,'Phone',true); assert.equal(result.code,'123456');
});

test('enrollment polls at most once a second with CSRF; approval refreshes session', async () => {
  let time = 0; const calls = [], sessions = [];
  const responses = [
    {csrf:'pending',code:'123456',expires:120}, {}, {},
    {csrf:'session',session:{id:'one',label:'Phone',expires:1000},status:{available:false,connected:false}},
  ];
  const flow = new SessionFlow({now:() => time, onSession:s => sessions.push(s), fetcher:async (path, options) => {
    calls.push({path,options}); return new Response(JSON.stringify(responses.shift()), {status:calls.length === 2 ? 202 : 200});
  }});
  await flow.enroll('Phone',true); assert.equal(flow.state.code,'123456');
  await flow.poll(); assert.equal(calls.length,1);
  time = 1000; await flow.poll(); await flow.poll(); assert.equal(calls.length,2);
  assert.equal(calls[1].options.headers['X-CSRF-Token'],'pending'); assert.equal(calls[1].options.body,'{}');
  time = 2000; await flow.poll(); assert.equal(calls[3].path,'/api/session');
  assert.equal(sessions.length,1); assert.equal(flow.state.code,'');
  assert.ok(calls.every(c => c.options.cache === 'no-store' && c.options.credentials === 'same-origin'));
});

test('expired, cancelled and late enrollment responses cannot create a session or repoll', async () => {
  let time = 0, resolve, calls = 0;
  const flow = new SessionFlow({now:() => time, fetcher:async () => { calls++; return new Response('{"csrf":"pending","code":"123456","expires":1}'); }});
  await flow.enroll('Phone',true); time = 1001; await flow.poll(); assert.equal(calls,1); assert.equal(flow.state.code,'');
  flow.fetcher = () => new Promise(r => {resolve = r;});
  const waiting = flow.enroll('Phone',true); flow.cancel();
  resolve(new Response('{"csrf":"pending","code":"123456","expires":120}')); await waiting;
  assert.equal(flow.state.code,''); assert.equal(flow.pending,null);
});

test('logout uses session CSRF; failed refresh invalidates local enrollment state', async () => {
  const calls = []; let cleared = 0;
  const flow = new SessionFlow({onSession:s => { if (s === null) cleared++; }, fetcher:async (path,options) => {
    calls.push({path,options}); return path === '/api/logout' ? new Response('{}') : new Response('{}',{status:401});
  }});
  await flow.logout('session-csrf'); assert.equal(calls[0].options.headers['X-CSRF-Token'],'session-csrf');
  await flow.refresh(); assert.ok(cleared); assert.match(flow.state.message,/Enrollment required/i);
});

test('failed Stop closes socket without sending an endless Stop loop', () => {
  const f = fixture(); f.own(); f.remote.stop(); const n = f.s.sent.length;
  f.reply({kind:'ack',seq:f.s.sent.at(-1).seq,status:'failed',error:'closed'});
  assert.equal(f.s.sent.length,n); assert.equal(f.remote.state.ready,false);
});

test('input arriving before the resumed timer is fenced after suspension', () => {
  const f = fixture(); f.own(); f.elapse(3001);
  assert.equal(f.remote.command('tap'),false); assert.equal(f.remote.state.ready,false);
  assert.equal(f.s.sent.some(m => m.kind === 'tap'),false);
});

test('eight unacknowledged controls fence the socket and discard the queue', () => {
  const f = fixture(); f.own();
  for (let i=0;i<9;i++) f.remote.command('tap');
  assert.equal(f.remote.state.ready,false); assert.equal(f.remote.pending.size,0);
  assert.equal(f.s.sent.filter(m => m.kind === 'tap').length,8);
});

test('server-driven disconnect cancels gestures without allowing reentrant input', () => {
  const f=fixture(); f.own(); let tried=false;
  f.remote.onCancel=() => {tried=true; f.remote.command('release');};
  f.reply({kind:'status',available:false,connected:false});
  assert.equal(tried,true); assert.equal(f.s.sent.some(m => m.kind === 'release'),false);
});

test('observer background/offline never sends global Stop; owner cleanup is lease-bound', () => {
  for (const action of ['setVisible','setOnline']) {
    const observer=fixture(); observer.remote[action](false);
    assert.equal(observer.s.sent.length,1);
    const owner=fixture(); owner.own(); owner.remote[action](false);
    assert.deepEqual(owner.s.sent.at(-1),{version:1,seq:2,kind:'release_control',lease:'owned'});
  }
});

test('stale owner ack cannot issue global Stop against a successor', () => {
  const f=fixture(); f.own(); f.advance(1000); const seq=f.s.sent.at(-1).seq;
  f.reply({kind:'ack',seq,status:'failed',error:'not_owner'});
  assert.equal(f.s.sent.some(m => m.kind === 'stop'),false); assert.equal(f.remote.state.ready,false);
});
