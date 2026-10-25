import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../web/', import.meta.url);
async function worker() {
  const handlers = {}, stored = new Map(); let skipped = 0;
  const windows = [{id:'window-1',type:'window',url:'https://hub.test/'}];
  const cache = {addAll:async paths => { for (const path of paths) stored.set(path,{path}); }, match:async request => stored.get(typeof request === 'string' ? request : new URL(request.url).pathname)};
  const caches = {open:async () => cache, keys:async () => ['ott-shell-old','unrelated'], delete:async name => deleted.push(name)};
  const deleted = [];
  const self = {location:{origin:'https://hub.test'}, addEventListener:(kind,fn) => {handlers[kind] = fn;},
    clients:{matchAll:async () => windows}, skipWaiting:async () => skipped++};
  vm.runInNewContext(await readFile(new URL('sw.js',root),'utf8'), {self,caches,URL,Response});
  async function emit(kind, extras = {}) {
    let waited, response;
    handlers[kind]?.({...extras, waitUntil:p => {waited=p;}, respondWith:p => {response=p;}});
    await waited; return response;
  }
  return {emit,stored,windows,skipped:() => skipped,deleted,handlers};
}

test('worker caches only explicit static shell; API, queries and all POSTs bypass it', async () => {
  const w = await worker(); await w.emit('install'); assert.equal(w.skipped(),0);
  assert.ok(w.stored.has('/remote.js')); assert.equal([...w.stored.keys()].some(p => p.startsWith('/api')),false);
  for (const [path,method] of [['/api/session','GET'],['/api/enroll/complete','POST'],['/ws','GET'],['/remote.js?x=1','GET'],['/','POST']]) {
    assert.equal(await w.emit('fetch',{request:{url:`https://hub.test${path}`,method}}),undefined);
  }
  assert.equal((await w.emit('fetch',{request:{url:'https://hub.test/remote.js',method:'GET'}})).path,'/remote.js');
  await w.emit('activate'); assert.deepEqual(w.deleted,['ott-shell-old']); assert.equal(w.handlers.sync,undefined);
});

test('update activation requires inactive requester and no other open app tabs', async () => {
  const w = await worker();
  await w.emit('message',{data:{kind:'activate',inactive:false},source:w.windows[0]}); assert.equal(w.skipped(),0);
  w.windows.push({id:'window-2'});
  await w.emit('message',{data:{kind:'activate',inactive:true},source:w.windows[0]}); assert.equal(w.skipped(),0);
  w.windows.pop(); await w.emit('message',{data:{kind:'activate',inactive:true},source:w.windows[0]}); assert.equal(w.skipped(),1);
});

test('shell has local resources, accessible control labels and trust/install/enrollment order', async () => {
  const html = await readFile(new URL('index.html',root),'utf8');
  assert.ok(html.indexOf('id="trust"') < html.indexOf('id="installation"'));
  assert.ok(html.indexOf('id="installation"') < html.indexOf('id="enrollment"'));
  assert.match(html,/TV integration locked pending protected-link verification/);
  assert.match(html,/maxlength="32"/); assert.match(html,/aria-live="polite"/); assert.match(html,/id="controls" disabled/);
  assert.match(html,/id="stop"/); assert.match(html,/id="touchpad"[^>]*aria-label=/);
  assert.doesNotMatch(html,/<script[^>]*>\s*[^<\s]/); assert.doesNotMatch(html,/(?:src|href)="https?:/);
  const manifest = JSON.parse(await readFile(new URL('manifest.webmanifest',root),'utf8'));
  assert.equal(manifest.start_url,'/'); assert.equal(manifest.display,'standalone');
  for (const icon of manifest.icons) assert.ok((await readFile(new URL(icon.src.replace(/^\//,''),root))).length);
});
