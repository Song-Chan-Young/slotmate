// 공통 — API 호출, 로그인 상태, 날짜·표시 도우미
// 화면은 서버 응답을 그대로 보여줌 (화면 설계서 7절 마지막 항목)

const TOKEN_KEY = 'slotmate.token';
const USER_KEY = 'slotmate.user';

const Auth = {
  token() { return localStorage.getItem(TOKEN_KEY); },
  user() {
    try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; }
  },
  save(loginResponse) {
    localStorage.setItem(TOKEN_KEY, loginResponse.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(loginResponse.user));
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
  isAdmin() { return Auth.user()?.role === 'ADMIN'; },
  goLogin() {
    const next = location.pathname + location.search;
    location.href = '/login.html?next=' + encodeURIComponent(next);
  },
};

class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || '요청을 처리하지 못했습니다.');
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors || [];
  }
}

/** /api/v1 호출. 401 이면 저장된 토큰을 지움 */
async function api(path, { method = 'GET', body, query } = {}) {
  const url = new URL('/api/v1' + path, location.origin);
  Object.entries(query || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
  });
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = Auth.token();
  if (token) headers.Authorization = 'Bearer ' + token;

  let res;
  try {
    res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.' });
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    if (res.status === 401 && token) Auth.clear();
    throw new ApiError(res.status, data);
  }
  return data;
}

// ---- DOM 도우미 (사용자 입력은 textContent 로만 넣음) ----

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => {
    if (v === undefined || v === null || v === false) return;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (k === 'style') node.style.cssText = v;
    else node.setAttribute(k, v === true ? '' : v);
  });
  children.flat().forEach((c) => {
    if (c === null || c === undefined || c === false) return;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  });
  return node;
}

function toast(message) {
  const t = el('div', { class: 'toast', role: 'status' }, message);
  document.body.append(t);
  setTimeout(() => t.remove(), 2400);
}

/** 확인·안내 모달. buttons: [{label, class, value}] → 누른 버튼의 value 로 resolve */
function modal(title, content, buttons = [{ label: '확인', class: 'primary', value: true }]) {
  return new Promise((resolve) => {
    const close = (value) => { backdrop.remove(); resolve(value); };
    const backdrop = el('div', { class: 'modal-backdrop', onclick: (e) => { if (e.target === backdrop) close(undefined); } },
      el('div', { class: 'modal card', role: 'dialog', 'aria-modal': 'true' },
        el('h2', {}, title),
        content,
        el('div', { class: 'modal-actions' },
          buttons.map((b) => el('button', { class: 'btn ' + (b.class || ''), onclick: () => close(b.value) }, b.label)))));
    document.body.append(backdrop);
    backdrop.querySelector('.modal-actions .btn:last-child')?.focus();
  });
}

// ---- 날짜·시간 (서버는 항상 +09:00 으로 응답) ----

const SEOUL = 'Asia/Seoul';
const DOW = ['일', '월', '화', '수', '목', '금', '토'];

function todaySeoul() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: SEOUL }).format(new Date());
}
function addDays(ymd, n) {
  const d = new Date(ymd + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
function dayOfWeek(ymd) { return new Date(ymd + 'T00:00:00Z').getUTCDay(); }
function hhmm(iso) { return iso.slice(11, 16); }
function ymdOf(iso) { return iso.slice(0, 10); }
function minutesBetween(a, b) { return Math.round((new Date(b) - new Date(a)) / 60000); }
function prettyDate(ymd) {
  const [, m, d] = ymd.split('-');
  return `${Number(m)}월 ${Number(d)}일 (${DOW[dayOfWeek(ymd)]})`;
}

const PROGRAM_TYPES = { PT: 'PT', YOGA: '요가', GX: 'GX', PILATES: '필라테스', ETC: '기타' };

const SESSION_STATUS = {
  DRAFT: ['준비 중', 'off'],
  OPEN: ['예약 오픈', 'ok'],
  CLOSED: ['마감', 'wait'],
  CANCELLED: ['취소', 'stop'],
  COMPLETED: ['종료', 'off'],
};
function statusBadge(status) {
  const [text, tone] = SESSION_STATUS[status] || [status, 'off'];
  return el('span', { class: 'badge ' + tone }, text);
}

// ---- 헤더 ----

function renderHeader(active) {
  const user = Auth.user();
  const nav = [['/', '수업 찾기', 'home']];
  if (user?.role === 'MEMBER') nav.push(['/my.html', '내 예약', 'my'], ['/notifications.html', '알림', 'notifications']);
  if (user?.role === 'ADMIN') {
    nav.push(['/admin/home.html', '대시보드', 'admin-home'], ['/admin/index.html', '수업 관리', 'admin-sessions'],
      ['/admin/programs.html', '프로그램', 'admin-programs'], ['/admin/members.html', '회원', 'admin-members']);
  }
  const account = user
    ? [el('span', {}, user.displayName, ' ', el('span', { class: 'badge ' + (user.role === 'ADMIN' ? 'wait' : 'off') }, user.role === 'ADMIN' ? '관리자' : '회원')),
       el('button', { class: 'btn small', onclick: () => { Auth.clear(); location.href = '/'; } }, '로그아웃')]
    : [el('a', { class: 'btn small primary', href: '/login.html?next=' + encodeURIComponent(location.pathname + location.search) }, '로그인')];

  const wide = document.querySelector('main.container.wide');
  document.body.prepend(el('header', { class: 'header' },
    el('div', { class: 'header-inner' + (wide ? ' wide' : '') },
      el('a', { class: 'logo', href: '/' }, 'Slot', el('span', {}, 'Mate')),
      el('nav', { class: 'nav' }, nav.map(([href, label, key]) =>
        el('a', { href, class: key === active ? 'active' : null }, label))),
      el('div', { class: 'account' }, account))));
  if (user?.role === 'MEMBER') showUnreadCount();
}

/** 헤더 알림 메뉴 옆 읽지 않은 알림 수 (화면 설계서 3절) */
async function showUnreadCount() {
  try {
    const page = await api('/my/notifications', { query: { unreadOnly: true, size: 1 } });
    const link = document.querySelector('.nav a[href="/notifications.html"]');
    link?.querySelector('.count')?.remove();
    if (link && page.page.totalElements > 0) {
      link.append(' ', el('span', { class: 'count' }, page.page.totalElements > 99 ? '99+' : page.page.totalElements));
    }
  } catch {
    // 개수 표시는 보조 정보라 실패해도 화면은 그대로
  }
}
