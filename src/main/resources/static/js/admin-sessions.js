// A02 주간 시간표 (GET /admin/sessions) · A03 수업 등록/수정/상태 변경

requireAdmin();
renderHeader('admin-sessions');

const HOUR_PX = 72;
// API 명세 v0.2 13.5 상태 전이표 — 버튼 표시용. 최종 판단은 서버
const TRANSITIONS = {
  DRAFT: ['OPEN', 'CANCELLED'],
  OPEN: ['CLOSED', 'CANCELLED'],
  CLOSED: ['OPEN', 'CANCELLED'],
  CANCELLED: [],
  COMPLETED: [],
};
const TRANSITION_LABEL = { OPEN: '예약 오픈', CLOSED: '예약 마감', CANCELLED: '수업 취소' };

function mondayOf(ymd) {
  const dow = dayOfWeek(ymd);
  return addDays(ymd, dow === 0 ? -6 : 1 - dow);
}

const state = { weekStart: mondayOf(new URLSearchParams(location.search).get('week') || todaySeoul()), type: '', status: '' };
let programs = [];
let instructors = [];

// ---- 필터 ----

const typeSelect = document.getElementById('type');
typeSelect.append(...typeOptions(null));
typeSelect.onchange = () => { state.type = typeSelect.value; load(); };
const statusSelect = document.getElementById('status');
Object.entries(SESSION_STATUS).forEach(([v, [l]]) => statusSelect.append(el('option', { value: v }, l)));
statusSelect.onchange = () => { state.status = statusSelect.value; load(); };

document.getElementById('prev').onclick = () => { state.weekStart = addDays(state.weekStart, -7); load(); };
document.getElementById('next').onclick = () => { state.weekStart = addDays(state.weekStart, 7); load(); };
document.getElementById('today').onclick = () => { state.weekStart = mondayOf(todaySeoul()); load(); };
document.getElementById('create').onclick = () => openForm(null);

document.getElementById('legend').replaceChildren(
  ...Object.keys(SESSION_STATUS).map((s) => statusBadge(s)),
  el('span', { class: 'muted', style: 'font-size:13px' }, '블록: 프로그램 · 시간 · 확정/정원 · 대기'));

// ---- 시간표 ----

async function load() {
  const from = state.weekStart;
  const to = addDays(from, 6);
  history.replaceState(null, '', from === mondayOf(todaySeoul()) ? location.pathname : '?week=' + from);
  document.getElementById('week-label').textContent = `${prettyDate(from)} – ${prettyDate(to)}`;
  try {
    const page = await api('/admin/sessions', { query: { from, to, type: state.type, status: state.status, size: 500 } });
    renderGrid(page.items);
    renderDayList(page.items);
  } catch (e) {
    if (handleAdminError(e)) return;
    document.getElementById('timetable').replaceChildren(el('div', { class: 'empty', style: 'grid-column:1/-1' }, e.message));
  }
}

function minutesOfDay(iso) {
  return Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16));
}

function renderGrid(items) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(state.weekStart, i));
  let startHour = 6;
  let endHour = 22;
  items.forEach((s) => {
    startHour = Math.min(startHour, Math.floor(minutesOfDay(s.startAt) / 60));
    const endMin = ymdOf(s.endAt) === ymdOf(s.startAt) ? minutesOfDay(s.endAt) : 24 * 60;
    endHour = Math.max(endHour, Math.ceil(endMin / 60));
  });
  const height = (endHour - startHour) * HOUR_PX;
  const top = (min) => ((min - startHour * 60) / 60) * HOUR_PX;
  const today = todaySeoul();

  const cells = [el('div', { class: 'tt-head' })];
  days.forEach((d) => cells.push(el('div', { class: 'tt-head' + (d === today ? ' today' : '') },
    DOW[dayOfWeek(d)], el('small', {}, `${Number(d.slice(5, 7))}/${Number(d.slice(8))}`))));

  const hours = el('div', { class: 'tt-hours', style: `height:${height}px` });
  for (let h = startHour; h < endHour; h++) {
    hours.append(el('span', { class: 'tt-hour' + (h === startHour ? ' first' : ''), style: `top:${(h - startHour) * HOUR_PX}px` },
      `${String(h).padStart(2, '0')}:00`));
  }
  cells.push(hours);

  days.forEach((d) => {
    const col = el('div', { class: 'tt-day', style: `height:${height}px` });
    for (let h = startHour + 1; h < endHour; h++) col.append(el('div', { class: 'tt-line', style: `top:${(h - startHour) * HOUR_PX}px` }));
    layoutLanes(items.filter((s) => ymdOf(s.startAt) === d)).forEach(({ s, lane, lanes }) => {
      const startMin = minutesOfDay(s.startAt);
      const duration = Math.max(minutesBetween(s.startAt, s.endAt), 25);
      const blockHeight = Math.min(duration / 60 * HOUR_PX, height - top(startMin)) - 2;
      const counts = `${s.confirmedCount}/${s.capacity}${s.waitingCount ? ` · 대기 ${s.waitingCount}` : ''}`;
      const statusText = SESSION_STATUS[s.sessionStatus][0];
      // 3줄이 안 들어가는 짧은 수업은 2줄로. 칸을 나눈 블록은 이름을 줄바꿈하고 시작 시각·인원만 (상태는 색으로)
      const narrow = lanes > 1;
      const compact = !narrow && blockHeight < 56;
      const time = narrow ? `${hhmm(s.startAt)} · ${counts}` : `${hhmm(s.startAt)}–${hhmm(s.endAt)}`;
      col.append(el('button', {
        class: 'tt-block s-' + s.sessionStatus + (narrow ? ' narrow' : ''),
        style: `top:${top(startMin)}px;height:${blockHeight}px;`
          + `left:calc(3px + (100% - 6px) * ${lane} / ${lanes});width:calc((100% - 6px) / ${lanes} - 2px)`,
        title: `${s.programName} ${hhmm(s.startAt)}–${hhmm(s.endAt)} · ${counts} · ${statusText}`,
        onclick: () => openForm(s),
      },
      el('b', {}, s.programName),
      narrow
        ? el('span', {}, time)
        : compact
          ? el('span', {}, `${hhmm(s.startAt)} · ${counts} · ${statusText}`)
          : [el('span', {}, time), el('span', {}, `${counts} · ${statusText}`)]));
    });
    cells.push(col);
  });
  document.getElementById('timetable').replaceChildren(...cells);
}

/** 시간이 겹치는 수업은 같은 묶음 안에서 칸을 나눠 나란히 놓음 → [{ s, lane, lanes }] */
function layoutLanes(daySessions) {
  const sorted = [...daySessions].sort((a, b) => a.startAt.localeCompare(b.startAt) || a.endAt.localeCompare(b.endAt));
  const result = [];
  let group = [];
  let laneEnds = [];
  let groupEnd = '';
  const flush = () => {
    group.forEach((g) => { g.lanes = laneEnds.length; });
    result.push(...group);
    group = [];
    laneEnds = [];
  };
  sorted.forEach((s) => {
    if (group.length && s.startAt >= groupEnd) flush();
    let lane = laneEnds.findIndex((end) => end <= s.startAt);
    if (lane === -1) lane = laneEnds.length;
    laneEnds[lane] = s.endAt;
    group.push({ s, lane });
    groupEnd = group.length === 1 || s.endAt > groupEnd ? s.endAt : groupEnd;
  });
  flush();
  return result;
}

/** 좁은 화면 — 날짜별 목록 (화면 설계서 A02) */
function renderDayList(items) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(state.weekStart, i));
  document.getElementById('day-list').replaceChildren(...days.map((d) => {
    const list = items.filter((s) => ymdOf(s.startAt) === d);
    return el('section', { class: 'day-group' },
      el('h3', {}, prettyDate(d)),
      list.length ? list.map((s) => el('button', { class: 'day-item', onclick: () => openForm(s) },
        el('span', { class: 't' }, hhmm(s.startAt)),
        el('span', { class: 'n' }, s.programName, el('br'),
          el('span', { class: 'muted', style: 'font-size:13px' },
            `확정 ${s.confirmedCount}/${s.capacity}`, s.waitingCount ? ` · 대기 ${s.waitingCount}` : '')),
        statusBadge(s.sessionStatus)))
        : el('p', { class: 'muted', style: 'margin:0 0 8px' }, '수업 없음'));
  }));
}

// ---- 등록·수정 폼 ----

async function loadPrograms() {
  const [page, instructorList] = await Promise.all([
    api('/admin/programs', { query: { size: 500 } }),
    api('/admin/instructors'),
  ]);
  programs = page.items;
  instructors = instructorList;
}

function input(id, attrs = {}) { return el('input', { id, ...attrs }); }

async function openForm(session) {
  if (!programs.length) {
    try { await loadPrograms(); } catch (e) { if (handleAdminError(e)) return; }
  }
  if (!session && !programs.some((p) => p.active)) {
    await modal('먼저 프로그램을 등록해 주세요', el('p', {}, '수업(회차)은 프로그램을 골라서 만듭니다.'),
      [{ label: '프로그램 관리로', class: 'primary', value: true }]);
    location.href = '/admin/programs.html';
    return;
  }
  const editing = !!session;
  const date = input('f-date', { type: 'date', required: true, value: editing ? ymdOf(session.startAt) : (state.weekStart <= todaySeoul() && todaySeoul() <= addDays(state.weekStart, 6) ? todaySeoul() : state.weekStart) });
  const start = input('f-start', { type: 'time', required: true, value: editing ? hhmm(session.startAt) : '10:00' });
  const end = input('f-end', { type: 'time', required: true, value: editing ? hhmm(session.endAt) : '' });
  const capacity = input('f-capacity', { type: 'number', min: 1, required: true, value: editing ? session.capacity : '' });
  // 비활성 강사는 지금 배정된 경우만 보임 (새로 고르면 서버가 409)
  const instructor = el('select', { id: 'f-instructor' },
    el('option', { value: '' }, '미정'),
    instructors.filter((i) => i.active || i.instructorId === session?.instructorId).map((i) =>
      el('option', { value: i.instructorId, selected: i.instructorId === session?.instructorId },
        `${i.name}${i.specialty ? ` · ${i.specialty}` : ''}${i.active ? '' : ' · 비활성'}`)));
  const location_ = input('f-location', { maxlength: 150, value: editing ? (session.location ?? '') : '' });

  const programSelect = el('select', { id: 'f-program', required: true, disabled: editing },
    programs.filter((p) => p.active || p.programId === session?.programId).map((p) =>
      el('option', { value: p.programId, selected: p.programId === session?.programId },
        `${p.name} (${PROGRAM_TYPES[p.type]})${p.active ? '' : ' · 비활성'}`)));
  const fillDefaults = () => {
    const p = programs.find((x) => String(x.programId) === programSelect.value);
    if (!p) return;
    capacity.value = p.defaultCapacity;
    const [h, m] = start.value.split(':').map(Number);
    const endMin = h * 60 + m + p.defaultDurationMinutes;
    end.value = endMin < 24 * 60 ? `${String(Math.floor(endMin / 60)).padStart(2, '0')}:${String(endMin % 60).padStart(2, '0')}` : '23:59';
  };
  if (!editing) {
    programSelect.onchange = fillDefaults;
    start.addEventListener('change', fillDefaults);
    fillDefaults();
  }

  const form = el('form', { novalidate: true },
    field('프로그램', programSelect, 'programId', editing ? '(수정 불가)' : null),
    el('div', { class: 'row' }, field('날짜', date, 'startAt'), field('시작', start, 'startAt'), field('종료', end, 'endAt')),
    el('div', { class: 'row' }, field('정원', capacity, 'capacity'), field('강사', instructor, 'instructorId', '(선택)')),
    field('장소', location_, 'location', '(선택)'));

  let description;
  let initialStatus;
  if (!editing) {
    description = el('textarea', { id: 'f-description', placeholder: '회원에게 보이는 유의 사항' });
    initialStatus = el('select', { id: 'f-status' },
      el('option', { value: 'DRAFT' }, '준비 중 (회원에게 안 보임)'),
      el('option', { value: 'OPEN' }, '예약 오픈'));
    form.append(field('설명', description, 'description', '(선택)'), field('등록 상태', initialStatus, 'initialStatus'));
  }

  const toIso = (t) => `${date.value}T${t}:00+09:00`;
  const onSubmit = async () => {
    const body = {
      startAt: toIso(start.value),
      endAt: toIso(end.value),
      capacity: capacity.value === '' ? null : Number(capacity.value),
      instructorId: instructor.value === '' ? null : Number(instructor.value),
      location: location_.value.trim() || null,
    };
    if (!date.value || !start.value || !end.value) {
      showFormErrors(form, { message: '날짜와 시작·종료 시각을 입력해 주세요.', fieldErrors: [] });
      return false;
    }
    if (editing && (body.startAt !== session.startAt || body.endAt !== session.endAt)
        && session.confirmedCount + session.waitingCount > 0) {
      const ok = await modal('예약자가 있는 수업입니다', el('p', {}, '일시를 바꾸면 서버에서 거절될 수 있습니다. 계속할까요?'),
        [{ label: '취소', value: false }, { label: '계속', class: 'primary', value: true }]);
      if (!ok) return false;
    }
    try {
      if (editing) {
        await api(`/admin/sessions/${session.sessionId}`, { method: 'PATCH', body });
        toast('수업을 수정했습니다');
      } else {
        await api('/admin/sessions', { method: 'POST', body: {
          ...body, programId: Number(programSelect.value),
          description: description.value.trim() || null, initialStatus: initialStatus.value,
        } });
        toast('수업을 등록했습니다');
      }
      load();
      return true;
    } catch (e) {
      if (handleAdminError(e)) return true;
      showFormErrors(form, e);
      return false;
    }
  };

  const extra = editing ? statusPanel(session) : null;
  await formModal(editing ? '수업 수정' : '수업 등록', form, editing ? '저장' : '등록', onSubmit, extra);
}

function statusPanel(s) {
  const buttons = TRANSITIONS[s.sessionStatus].map((target) => el('button', {
    class: 'btn small ' + (target === 'CANCELLED' ? 'danger' : ''),
    type: 'button',
    onclick: () => changeStatus(s, target),
  }, TRANSITION_LABEL[target]));
  return el('div', { class: 'card', style: 'background:var(--bg);box-shadow:none;padding:12px;margin-bottom:16px' },
    el('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' },
      statusBadge(s.sessionStatus),
      el('span', { class: 'muted', style: 'font-size:13px' },
        `확정 ${s.confirmedCount}/${s.capacity} · 대기 ${s.waitingCount}`),
      el('a', { class: 'btn small', href: `/admin/reservations.html?sessionId=${s.sessionId}&date=${ymdOf(s.startAt)}` },
        '예약·대기 관리'),
      el('span', { style: 'flex:1' }),
      buttons.length ? buttons : el('span', { class: 'muted', style: 'font-size:13px' }, '상태 변경 불가')));
}

async function changeStatus(s, target) {
  if (target === 'CANCELLED') {
    const ok = await modal('수업을 취소할까요?', el('p', {},
      `${s.programName} · ${prettyDate(ymdOf(s.startAt))} ${hhmm(s.startAt)}. 취소한 수업은 다시 열 수 없습니다.`),
      [{ label: '닫기', value: false }, { label: '수업 취소', class: 'primary', value: true }]);
    if (!ok) return;
  }
  try {
    await api(`/admin/sessions/${s.sessionId}/status`, { method: 'PATCH', body: { status: target } });
    document.querySelectorAll('.modal-backdrop').forEach((m) => m.remove());
    toast(`상태를 「${SESSION_STATUS[target][0]}」(으)로 바꿨습니다`);
    load();
  } catch (e) {
    if (handleAdminError(e)) return;
    await modal('상태를 바꾸지 못했습니다', el('p', {}, e.message));
  }
}

load();
