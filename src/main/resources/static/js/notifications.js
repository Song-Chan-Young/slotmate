// M06 — GET /my/notifications (최신순). 누르면 읽음 처리(PATCH …/read) 후 관련 수업으로 이동

const PAGE_SIZE = 20;

if (!Auth.token()) Auth.goLogin();
renderHeader('notifications');

const state = { unreadOnly: false, page: 0, items: [], totalPages: 0 };

function ago(iso) {
  const minutes = Math.floor((Date.now() - new Date(iso)) / 60000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}시간 전`;
  return `${prettyDate(ymdOf(iso))} ${hhmm(iso)}`;
}

function renderFilters() {
  document.getElementById('filters').replaceChildren(...[[false, '전체'], [true, '읽지 않음']].map(([value, label]) =>
    el('button', {
      class: 'chip' + (state.unreadOnly === value ? ' active' : ''),
      'aria-pressed': state.unreadOnly === value ? 'true' : 'false',
      onclick: () => { state.unreadOnly = value; reload(); },
    }, label)));
}

async function openNotice(n) {
  if (!n.read) {
    try {
      await api(`/my/notifications/${n.notificationId}/read`, { method: 'PATCH' });
    } catch (e) {
      if (e.status === 401) { Auth.goLogin(); return; }
    }
  }
  if (n.sessionId) location.href = `/session.html?id=${n.sessionId}`;
  else reload();
}

function item(n) {
  return el('button', { class: 'notice' + (n.read ? '' : ' unread'), onclick: () => openNotice(n) },
    el('div', { class: 'head' },
      el('b', {}, n.title, n.read ? null : el('span', { class: 'badge ok', style: 'margin-left:8px' }, '새 알림')),
      el('span', { class: 'when' }, ago(n.createdAt))),
    el('p', {}, n.message));
}

function render() {
  document.getElementById('list').replaceChildren(...(state.items.length
    ? state.items.map(item)
    : [el('div', { class: 'empty' },
        el('p', {}, state.unreadOnly ? '읽지 않은 알림이 없습니다' : '아직 알림이 없습니다'),
        el('a', { class: 'btn primary', href: '/' }, '수업 찾기'))]));
  document.getElementById('more').classList.toggle('hidden', state.page + 1 >= state.totalPages);
}

async function loadPage() {
  try {
    const page = await api('/my/notifications', { query: { unreadOnly: state.unreadOnly, page: state.page, size: PAGE_SIZE } });
    state.items.push(...page.items);
    state.totalPages = page.page.totalPages;
    render();
  } catch (e) {
    if (e.status === 401 || e.status === 403) { Auth.clear(); Auth.goLogin(); return; }
    document.getElementById('list').replaceChildren(el('div', { class: 'empty' },
      el('p', {}, e.message), el('button', { class: 'btn', onclick: reload }, '다시 시도')));
  }
}

function reload() {
  state.page = 0;
  state.items = [];
  renderFilters();
  document.getElementById('list').replaceChildren(el('div', { class: 'empty' }, '불러오는 중…'));
  loadPage();
}

document.getElementById('more').onclick = () => { state.page++; loadPage(); };

reload();
