// M01·M02 — 날짜·카테고리·종류를 바꾸면 GET /sessions?date= 로 시간순 카드 목록 (카테고리는 확장 설계 v0.1 5절)

const DATE_RANGE = 14;

const params = new URLSearchParams(location.search);
const state = {
  date: params.get('date') || todaySeoul(),
  category: params.get('category') || '',
  type: params.get('type') || '',
  keyword: params.get('keyword') || '',
};

renderHeader('home');
const user = Auth.user();
if (user) document.getElementById('greeting').textContent = `${user.displayName}님, 안녕하세요`;

function syncUrl() {
  const q = new URLSearchParams();
  if (state.date !== todaySeoul()) q.set('date', state.date);
  if (state.category) q.set('category', state.category);
  if (state.type) q.set('type', state.type);
  if (state.keyword) q.set('keyword', state.keyword);
  history.replaceState(null, '', q.toString() ? '?' + q : location.pathname);
}

function renderDates() {
  const today = todaySeoul();
  const start = state.date < today ? state.date : addDays(today, -1);
  const box = document.getElementById('dates');
  box.replaceChildren(...Array.from({ length: DATE_RANGE }, (_, i) => {
    const d = addDays(start, i);
    return el('button', {
      class: 'date-chip' + (d === state.date ? ' active' : ''),
      'aria-pressed': d === state.date ? 'true' : 'false',
      onclick: () => { state.date = d; refresh(); },
    }, el('span', { class: 'dow' }, d === today ? '오늘' : DOW[dayOfWeek(d)]), el('span', { class: 'day' }, Number(d.slice(8))));
  }));
  box.querySelector('.active')?.scrollIntoView({ block: 'nearest', inline: 'center' });
  document.getElementById('selected-date').textContent = prettyDate(state.date);
}

function renderCategories() {
  const tabs = [['', '전체'], ...Object.entries(CATEGORIES).map(([value, [label]]) => [value, label])];
  document.getElementById('categories').replaceChildren(...tabs.map(([value, label]) =>
    el('button', {
      class: state.category === value ? 'active' : null,
      role: 'tab',
      'aria-selected': state.category === value ? 'true' : 'false',
      onclick: () => { state.category = value; state.type = ''; refresh(); },
    }, label)));
}

/** 카테고리를 고르면 그 안의 세부 종류 칩 */
function renderTypes() {
  const types = state.category ? CATEGORIES[state.category][1] : [];
  const filters = types.length ? [['', '전체'], ...types.map((t) => [t, PROGRAM_TYPES[t]])] : [];
  document.getElementById('types').replaceChildren(...filters.map(([value, label]) =>
    el('button', {
      class: 'chip' + (state.type === value ? ' active' : ''),
      'aria-pressed': state.type === value ? 'true' : 'false',
      onclick: () => { state.type = value; refresh(); },
    }, label)));
  document.getElementById('types').style.cssText = 'display:flex;gap:8px;flex-wrap:wrap';
}

function sessionCard(item) {
  const [badgeText, tone] = bookingView(item);
  const duration = minutesBetween(item.startAt, item.endAt);
  return el('article', { class: 'session-card' },
    el('div', { class: 'time' }, hhmm(item.startAt), el('small', {}, `${duration}분`)),
    el('div', {},
      el('div', { class: 'title' }, el('a', { href: `/session.html?id=${item.sessionId}` }, item.programName)),
      el('div', { class: 'meta' }, PROGRAM_TYPES[item.programType] || item.programType,
        item.instructorName ? ` · ${item.instructorName}` : ''),
      el('div', { class: 'counts' }, countsLine(item))),
    el('div', { class: 'action' },
      el('span', { class: 'badge ' + tone }, badgeText),
      bookingButton(item, refresh)));
}

function renderSummary(items) {
  const box = document.getElementById('summary');
  if (!Auth.token()) { box.replaceChildren(); return; }
  const confirmed = items.filter((i) => i.myReservationStatus === 'CONFIRMED').length;
  const waiting = items.filter((i) => i.myReservationStatus === 'WAITING').length;
  box.replaceChildren(
    el('span', { class: 'badge ok' }, `이 날 예약 확정 ${confirmed}건`),
    el('span', { class: 'badge wait' }, `대기 ${waiting}건`));
}

let requestSeq = 0;
async function refresh() {
  syncUrl();
  renderDates();
  renderCategories();
  renderTypes();
  const seq = ++requestSeq;
  const list = document.getElementById('list');
  list.replaceChildren(el('div', { class: 'empty' }, '불러오는 중…'));
  try {
    const page = await api('/sessions', {
      query: { date: state.date, category: state.category, type: state.type, keyword: state.keyword, size: 100 },
    });
    if (seq !== requestSeq) return;
    renderSummary(page.items);
    list.replaceChildren(...(page.items.length
      ? page.items.map(sessionCard)
      : [el('div', { class: 'empty' },
          el('p', {}, state.category ? `선택한 날짜에 ${CATEGORIES[state.category][0]} 일정이 없습니다` : '선택한 날짜에 등록된 일정이 없습니다'),
          el('button', { class: 'btn', onclick: () => { state.date = addDays(state.date, 1); refresh(); } }, '다음 날 보기'))]));
  } catch (e) {
    if (seq !== requestSeq) return;
    list.replaceChildren(el('div', { class: 'empty' },
      el('p', {}, e.message), el('button', { class: 'btn', onclick: refresh }, '다시 시도')));
  }
}

let keywordTimer;
const keywordInput = document.getElementById('keyword');
keywordInput.value = state.keyword;
keywordInput.addEventListener('input', () => {
  clearTimeout(keywordTimer);
  keywordTimer = setTimeout(() => { state.keyword = keywordInput.value.trim(); refresh(); }, 300);
});

refresh();
