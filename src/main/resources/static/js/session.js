// M03 — GET /sessions/{id}

renderHeader('home');
const sessionId = new URLSearchParams(location.search).get('id');
const box = document.getElementById('detail');

async function load() {
  try {
    const s = await api(`/sessions/${encodeURIComponent(sessionId)}`);
    document.title = `SlotMate — ${s.programName}`;
    document.getElementById('back').href = '/?date=' + ymdOf(s.startAt);
    const [badgeText, tone] = bookingView(s);
    const rows = [
      ['종류', `${CATEGORIES[s.category]?.[0] || s.category} · ${PROGRAM_TYPES[s.programType] || s.programType}`],
      ['일시', `${prettyDate(ymdOf(s.startAt))} ${hhmm(s.startAt)}–${hhmm(s.endAt)} (${minutesBetween(s.startAt, s.endAt)}분)`],
      ['강사', s.instructorName || '미정'],
      ['장소', s.location],
      ['정원', `${s.capacity}명 · 확정 ${s.confirmedCount}명 · 잔여 ${s.availableSeats}명`],
      ['대기', `${s.waitingCount}명`],
      ['예약 인원', s.maxPartySize > 1 ? `한 번에 최대 ${s.maxPartySize}명` : null],
      ['수업 상태', statusBadge(s.sessionStatus)],
    ].filter(([, v]) => v !== null && v !== undefined && v !== '');

    box.replaceChildren(el('article', { class: 'card' },
      el('div', { style: 'display:flex;justify-content:space-between;gap:12px;align-items:flex-start' },
        el('h1', {}, s.programName),
        el('span', { class: 'badge ' + tone }, badgeText)),
      s.programDescription ? el('p', {}, s.programDescription) : null,
      el('dl', { class: 'detail-grid' }, rows.flatMap(([k, v]) => [el('dt', {}, k), el('dd', {}, v)])),
      s.description ? el('div', { class: 'card', style: 'background:var(--bg);box-shadow:none;padding:14px' },
        el('b', {}, '유의 사항'), el('p', { style: 'margin:6px 0 0' }, s.description)) : null,
      el('div', { style: 'margin-top:20px' }, bookingButton(s, load)),
      el('p', { class: 'muted', style: 'font-size:13px;margin-bottom:0' },
        '잔여석은 조회 시점 기준입니다. 예약 결과는 신청 후 서버 응답으로 확정됩니다.')));
    box.querySelector('.btn').style.width = '100%';
  } catch (e) {
    box.replaceChildren(el('div', { class: 'empty' },
      el('p', {}, e.status === 404 ? '수업을 찾을 수 없습니다.' : e.message),
      el('a', { class: 'btn', href: '/' }, '수업 목록으로')));
  }
}

load();
