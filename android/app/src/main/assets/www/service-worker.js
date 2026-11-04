/* SPDX-License-Identifier: Apache-2.0 */
const CACHE = 'nado-local-v1';
const ASSETS = ['./', 'index.html', 'app.js', 'settings.js', 'local-store.js', 'styles.css', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS))));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('nado-') && key !== CACHE).map(key => caches.delete(key))))));
self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET' || new URL(event.request.url).origin !== location.origin) return;
    event.respondWith(caches.match(event.request).then(cached => cached || fetch(event.request)));
});
