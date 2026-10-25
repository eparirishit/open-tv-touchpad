// Bump this version whenever any shell asset changes.
const CACHE = 'ott-shell-v1';
const ASSETS = ['/', '/remote.js', '/gestures.js', '/style.css', '/manifest.webmanifest', '/icons/icon.svg'];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(names => Promise.all(names.filter(name => name.startsWith('ott-shell-') && name !== CACHE).map(name => caches.delete(name)))));
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.search || !ASSETS.includes(url.pathname)) return;
  event.respondWith(caches.open(CACHE).then(cache => cache.match(event.request)).then(response => response || new Response('App shell unavailable. Reopen when online.', {status:503})));
});
self.addEventListener('message', event => {
  if (event.data?.kind !== 'activate' || event.data.inactive !== true || event.source?.type !== 'window') return;
  event.waitUntil(self.clients.matchAll({type:'window', includeUncontrolled:true}).then(clients => {
    // Another tab may own input: require it to be closed before replacing its worker.
    if (clients.length === 1 && clients[0].id === event.source.id && new URL(clients[0].url).origin === self.location.origin) return self.skipWaiting();
  }));
});
