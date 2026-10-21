// 서비스 워커 — 홈 화면 설치(PWA)용. 화면 파일만 캐시하고 API 는 절대 캐시하지 않음
// (예약 현황·내 정보는 항상 서버 최신값이어야 하고, 로그인 정보가 기기에 남으면 안 됨)
// 화면 파일을 바꿔 배포하면 VERSION 을 올림 → 이전 캐시 삭제
const VERSION = 'slotmate-v2';
const SHELL = ['/', '/index.html', '/login.html', '/my.html', '/notifications.html', '/session.html',
  '/css/app.css', '/js/common.js', '/icons/icon-192.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')
      || url.pathname.startsWith('/swagger-ui') || url.pathname.startsWith('/v3/')) {
    return; // 브라우저 기본 동작 (네트워크)
  }
  // 화면 파일: 네트워크 우선, 실패(오프라인)하면 캐시
  event.respondWith(fetch(event.request)
    .then((res) => {
      if (res.ok) {
        const copy = res.clone();
        caches.open(VERSION).then((cache) => cache.put(event.request, copy));
      }
      return res;
    })
    .catch(() => caches.match(event.request).then((hit) => hit || caches.match('/index.html'))));
});
