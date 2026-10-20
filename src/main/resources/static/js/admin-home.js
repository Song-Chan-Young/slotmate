// A01 관리자 홈 — 오늘 회차(GET /admin/sessions) 와 활성 회원 수(GET /admin/members) 로 요약

requireAdmin();
renderHeader('admin-home');

const today = todaySeoul();
document.getElementById('today-label').textContent = prettyDate(today);

function stat(label, value) {
  return el('div', { class: 'stat' }, el('div', { class: 'label' }, label), el('div', { class: 'value' }, value));
}

async function load() {
  const rows = document.getElementById('rows');
  try {
    const [sessions, members] = await Promise.all([
      api('/admin/sessions', { query: { from: today, to: today, size: 500 } }),
      api('/admin/members', { query: { status: 'ACTIVE', size: 1 } }),
    ]);
    // 취소된 수업은 운영 대상이 아니라 수업 수에서 뺌
    const live = sessions.items.filter((s) => s.sessionStatus !== 'CANCELLED');
    document.getElementById('stats').replaceChildren(
      stat('오늘 수업', `${live.length}개`),
      stat('확정 예약', `${live.reduce((n, s) => n + s.confirmedCount, 0)}명`),
      stat('대기 인원', `${live.reduce((n, s) => n + s.waitingCount, 0)}명`),
      stat('활성 회원', `${members.page.totalElements}명`));

    rows.replaceChildren(...(sessions.items.length
      ? sessions.items.map((s) => el('tr', {},
          el('td', {}, el('b', {}, hhmm(s.startAt)), el('span', { class: 'muted' }, `–${hhmm(s.endAt)}`)),
          el('td', {}, s.programName, el('br'), el('span', { class: 'muted', style: 'font-size:13px' },
            PROGRAM_TYPES[s.programType] || s.programType, s.location ? ` · ${s.location}` : '')),
          el('td', {}, s.instructorName || '미정'),
          el('td', {}, `${s.confirmedCount}/${s.capacity}`),
          el('td', {}, s.waitingCount ? el('span', { class: 'badge wait' }, `${s.waitingCount}명`) : '0'),
          el('td', {}, statusBadge(s.sessionStatus)),
          el('td', { style: 'text-align:right' }, el('a', { class: 'btn small',
            href: `/admin/reservations.html?sessionId=${s.sessionId}&date=${today}` }, '예약 관리'))))
      : [el('tr', {}, el('td', { colspan: 7, class: 'muted', style: 'text-align:center;padding:32px' },
          '오늘 등록된 수업이 없습니다'))]));
  } catch (e) {
    if (handleAdminError(e)) return;
    rows.replaceChildren(el('tr', {}, el('td', { colspan: 7, style: 'text-align:center;padding:32px' },
      e.message, ' ', el('button', { class: 'btn small', onclick: load }, '다시 시도'))));
  }
}

load();
