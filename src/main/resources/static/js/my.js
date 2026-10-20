// M05 — GET /my/reservations, 취소는 DELETE /reservations/{id} (확인 모달 후)

const STATUS_FILTERS = [['', '전체'], ['CONFIRMED', '예약 확정'], ['WAITING', '대기 중'], ['CANCELLED', '취소']];
const RESERVATION_VIEW = {
  CONFIRMED: ['예약 확정', 'ok'],
  WAITING: ['대기 중', 'wait'],
  CANCELLED: ['취소', 'off'],
};
const PAGE_SIZE = 20;

if (!Auth.token()) Auth.goLogin();
renderHeader('my');

const state = { status: new URLSearchParams(location.search).get('status') || '', page: 0, items: [], totalPages: 0 };

function renderFilters() {
  document.getElementById('filters').replaceChildren(...STATUS_FILTERS.map(([value, label]) =>
    el('button', {
      class: 'chip' + (state.status === value ? ' active' : ''),
      'aria-pressed': state.status === value ? 'true' : 'false',
      onclick: () => { state.status = value; reload(); },
    }, label)));
}

/** 시작 전인 확정·대기만 취소 가능. 최종 판단은 서버 */
function cancellable(r) {
  return (r.reservationStatus === 'CONFIRMED' || r.reservationStatus === 'WAITING') && new Date(r.startAt) > new Date();
}

function card(r) {
  const [text, tone] = RESERVATION_VIEW[r.reservationStatus] || [r.reservationStatus, 'off'];
  const badge = r.reservationStatus === 'WAITING' ? `대기 중 · ${r.waitingPosition}번째` : text;
  return el('article', { class: 'session-card' },
    el('div', { class: 'time' }, hhmm(r.startAt), el('small', {}, prettyDate(ymdOf(r.startAt)))),
    el('div', {},
      el('div', { class: 'title' }, el('a', { href: `/session.html?id=${r.sessionId}` }, r.programName)),
      el('div', { class: 'meta' }, PROGRAM_TYPES[r.programType] || r.programType,
        r.instructorName ? ` · ${r.instructorName}` : '', r.location ? ` · ${r.location}` : ''),
      r.sessionStatus === 'CANCELLED' ? el('div', { class: 'counts muted' }, '수업이 취소되었습니다') : null),
    el('div', { class: 'action' },
      el('span', { class: 'badge ' + tone }, badge),
      cancellable(r)
        ? el('button', { class: 'btn danger', onclick: (e) => cancel(r, e.currentTarget) },
          r.reservationStatus === 'WAITING' ? '대기 취소' : '예약 취소')
        : null));
}

async function cancel(r, button) {
  const what = r.reservationStatus === 'WAITING' ? '대기' : '예약';
  const ok = await modal(`${what}를 취소할까요?`,
    el('p', {}, `${prettyDate(ymdOf(r.startAt))} ${hhmm(r.startAt)} · ${r.programName}`),
    [{ label: '닫기', value: false }, { label: `${what} 취소`, class: 'primary', value: true }]);
  if (!ok) return;
  button.disabled = true;
  try {
    const result = await api(`/reservations/${r.reservationId}`, { method: 'DELETE' });
    toast(result.message);
  } catch (e) {
    if (e.status === 401) { Auth.goLogin(); return; }
    await modal('취소하지 못했습니다', el('p', {},
      e.code === 'RESERVATION_NOT_CANCELLABLE' ? '이미 취소했거나 시작한 수업은 취소할 수 없습니다.' : e.message));
  }
  reload();
}

function render() {
  const list = document.getElementById('list');
  list.replaceChildren(...(state.items.length
    ? state.items.map(card)
    : [el('div', { class: 'empty' },
        el('p', {}, state.status ? '해당하는 예약이 없습니다' : '아직 예약한 수업이 없습니다'),
        el('a', { class: 'btn primary', href: '/' }, '수업 찾기'))]));
  document.getElementById('more').classList.toggle('hidden', state.page + 1 >= state.totalPages);
}

async function loadPage() {
  try {
    const page = await api('/my/reservations', { query: { status: state.status, page: state.page, size: PAGE_SIZE } });
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
  history.replaceState(null, '', state.status ? '?status=' + state.status : location.pathname);
  state.page = 0;
  state.items = [];
  renderFilters();
  document.getElementById('list').replaceChildren(el('div', { class: 'empty' }, '불러오는 중…'));
  loadPage();
}

document.getElementById('more').onclick = () => { state.page++; loadPage(); };

reload();
