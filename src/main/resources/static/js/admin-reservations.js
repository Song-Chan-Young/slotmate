// A04 회차별 예약 관리 — GET /admin/sessions/{id}/reservations. 요약은 9.2 시간표 응답에서 찾음

requireAdmin();
renderHeader('admin-sessions');

const STATUS_FILTERS = [['', '전체'], ['CONFIRMED', '예약 확정'], ['WAITING', '대기 중'], ['CANCELLED', '취소']];
const RESERVATION_VIEW = { CONFIRMED: ['예약 확정', 'ok'], WAITING: ['대기 중', 'wait'], CANCELLED: ['취소', 'off'] };
const PAGE_SIZE = 50;

const params = new URLSearchParams(location.search);
const sessionId = params.get('sessionId');
const date = params.get('date');
const state = { status: '', page: 0, totalPages: 0 };

if (date) document.getElementById('back').href = '/admin/index.html?week=' + date;

function dateTime(iso) {
  return iso ? `${Number(iso.slice(5, 7))}/${Number(iso.slice(8, 10))} ${hhmm(iso)}` : '-';
}

async function loadSummary() {
  const box = document.getElementById('summary');
  if (!date) { box.replaceChildren(el('h1', {}, `회차 #${sessionId}`)); return; }
  try {
    const page = await api('/admin/sessions', { query: { from: date, to: date, size: 500 } });
    const s = page.items.find((x) => String(x.sessionId) === sessionId);
    if (!s) { box.replaceChildren(el('h1', {}, `회차 #${sessionId}`)); return; }
    box.replaceChildren(
      el('div', { style: 'display:flex;justify-content:space-between;gap:12px;align-items:flex-start' },
        el('h1', {}, s.programName), statusBadge(s.sessionStatus)),
      el('p', { class: 'muted', style: 'margin:0 0 12px' },
        `${prettyDate(ymdOf(s.startAt))} ${hhmm(s.startAt)}–${hhmm(s.endAt)}`,
        s.instructorName ? ` · ${s.instructorName}` : '', s.location ? ` · ${s.location}` : ''),
      el('div', { class: 'summary', style: 'margin:0' },
        el('span', { class: 'badge off' }, `정원 ${s.capacity}명`),
        el('span', { class: 'badge ok' }, `확정 ${s.confirmedCount}명`),
        el('span', { class: 'badge ok' }, `잔여 ${Math.max(0, s.capacity - s.confirmedCount)}석`),
        el('span', { class: 'badge wait' }, `대기 ${s.waitingCount}명`)));
  } catch (e) {
    if (handleAdminError(e)) return;
    box.replaceChildren(el('p', {}, e.message));
  }
}

function renderFilters() {
  document.getElementById('filters').replaceChildren(...STATUS_FILTERS.map(([value, label]) =>
    el('button', {
      class: 'chip' + (state.status === value ? ' active' : ''),
      'aria-pressed': state.status === value ? 'true' : 'false',
      onclick: () => { state.status = value; reload(); },
    }, label)));
}

/** 확정 시각이 신청 시각보다 늦으면 대기에서 자동 승격된 것 */
function row(r) {
  const [text, tone] = RESERVATION_VIEW[r.reservationStatus] || [r.reservationStatus, 'off'];
  const promoted = r.confirmedAt && new Date(r.confirmedAt) > new Date(r.requestedAt);
  return el('tr', {},
    el('td', {}, r.waitingPosition ? `대기 ${r.waitingPosition}` : '-'),
    el('td', {}, el('b', {}, r.memberDisplayName), el('br'), el('span', { class: 'muted', style: 'font-size:13px' }, r.memberEmail)),
    el('td', {}, el('span', { class: 'badge ' + tone }, text)),
    el('td', {}, dateTime(r.requestedAt)),
    el('td', {}, dateTime(r.confirmedAt), promoted ? el('span', { class: 'badge wait', style: 'margin-left:6px' }, '자동 승격') : null),
    el('td', {}, dateTime(r.cancelledAt)));
}

async function loadPage() {
  const rows = document.getElementById('rows');
  try {
    const page = await api(`/admin/sessions/${encodeURIComponent(sessionId)}/reservations`,
      { query: { status: state.status, page: state.page, size: PAGE_SIZE } });
    if (state.page === 0) rows.replaceChildren();
    rows.append(...page.items.map(row));
    if (!rows.children.length) {
      rows.append(el('tr', {}, el('td', { colspan: 6, class: 'muted', style: 'text-align:center;padding:32px' },
        state.status ? '해당하는 예약이 없습니다' : '아직 예약자가 없습니다')));
    }
    state.totalPages = page.page.totalPages;
    document.getElementById('more').classList.toggle('hidden', state.page + 1 >= state.totalPages);
  } catch (e) {
    if (handleAdminError(e)) return;
    rows.replaceChildren(el('tr', {}, el('td', { colspan: 6, style: 'text-align:center;padding:32px' },
      e.status === 404 ? '수업을 찾을 수 없습니다.' : e.message)));
  }
}

function reload() {
  state.page = 0;
  renderFilters();
  loadPage();
}

document.getElementById('more').onclick = () => { state.page++; loadPage(); };

loadSummary();
reload();
