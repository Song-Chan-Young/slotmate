// 회원 — 수업 카드 버튼·배지 규칙(화면 설계서 M01)과 예약 신청 결과(M04)

/** bookingAction → [배지 문구, 배지 색, 버튼 문구, 버튼 스타일(null 이면 비활성)] */
function bookingView(item) {
  switch (item.bookingAction) {
    case 'BOOKABLE': return ['예약 가능', 'ok', '예약하기', 'primary'];
    case 'WAITLISTABLE': return ['대기 신청 가능', 'wait', '대기 신청', 'wait'];
    case 'ALREADY_CONFIRMED': return ['예약 확정', 'ok', `예약 완료${partyText(item.myPartySize)}`, null];
    case 'ALREADY_WAITING': return ['대기 중', 'wait', `대기 중 · ${item.myWaitingPosition}번째${partyText(item.myPartySize)}`, null];
    case 'CLOSED': return ['마감', 'off', '예약 마감', null];
    case 'CANCELLED': return ['취소', 'stop', '취소된 수업', null];
    case 'COMPLETED': return ['종료', 'off', '종료된 수업', null];
    case 'STARTED': return ['시작됨', 'off', '이미 시작한 수업', null];
    default: return [item.bookingAction, 'off', '-', null];
  }
}

function bookingButton(item, onDone) {
  const [, , label, style] = bookingView(item);
  return el('button', {
    class: 'btn ' + (style || ''),
    disabled: !style,
    onclick: style ? (e) => reserve(item, e.currentTarget, onDone) : null,
  }, label);
}

function countsLine(item) {
  return [
    el('span', {}, `예약 ${item.confirmedCount}/${item.capacity}`),
    el('span', { class: item.availableSeats > 0 ? '' : 'muted' }, `잔여 ${item.availableSeats}명`),
    item.waitingCount > 0 ? el('span', { class: 'muted' }, `대기 ${item.waitingCount}명`) : null,
  ];
}

const RESERVE_ERRORS = {
  DUPLICATE_ACTIVE_RESERVATION: '이미 이 수업에 예약 또는 대기 신청이 되어 있습니다.',
  SESSION_NOT_OPEN: '지금은 예약을 받지 않는 수업입니다. 마감되었거나 이미 시작했을 수 있습니다.',
  RESOURCE_NOT_FOUND: '수업을 찾을 수 없습니다. 목록을 새로 고쳐 주세요.',
  FORBIDDEN: '회원 계정으로만 예약할 수 있습니다.',
  INVALID_PARTY_SIZE: '예약 인원을 확인해 주세요. 한 번에 예약할 수 있는 인원을 넘었을 수 있습니다.',
};

/** 확장 설계 v0.1 5절 — 최대 인원이 2명 이상인 상품만 인원 선택. 취소하면 null */
async function pickParty(item) {
  const max = Math.min(item.maxPartySize, item.capacity);
  let count = 1;
  const value = el('b', { style: 'font-size:22px;min-width:56px;text-align:center' });
  const hint = el('p', { class: 'muted', style: 'margin:10px 0 0' });
  const minus = el('button', { class: 'btn', 'aria-label': '인원 줄이기', onclick: () => set(count - 1) }, '−');
  const plus = el('button', { class: 'btn', 'aria-label': '인원 늘리기', onclick: () => set(count + 1) }, '+');
  function set(n) {
    count = Math.max(1, Math.min(max, n));
    value.textContent = `${count}명`;
    minus.disabled = count === 1;
    plus.disabled = count === max;
    hint.textContent = count <= item.availableSeats
      ? `남은 자리 ${item.availableSeats} — 바로 확정됩니다.`
      : `남은 자리 ${item.availableSeats} — ${count}명이 함께 대기합니다. 자리가 나면 순서대로 자동 확정돼요.`;
  }
  set(1);
  const ok = await modal('몇 명이 예약할까요?',
    el('div', {},
      el('div', { style: 'display:flex;align-items:center;justify-content:center;gap:16px' }, minus, value, plus),
      hint,
      el('p', { class: 'muted', style: 'font-size:13px;margin:6px 0 0' }, `한 번에 최대 ${max}명`)),
    [{ label: '취소', value: false }, { label: '신청하기', class: 'primary', value: true }]);
  return ok ? count : null;
}

async function reserve(item, button, onDone) {
  if (!Auth.token()) { Auth.goLogin(); return; }
  const partySize = item.maxPartySize > 1 ? await pickParty(item) : 1;
  if (partySize === null) return;
  button.disabled = true;
  try {
    const result = await api(`/sessions/${item.sessionId}/reservations`, { method: 'POST', body: { partySize } });
    const when = `${prettyDate(ymdOf(item.startAt))} ${hhmm(item.startAt)} · ${item.programName}${partyText(result.partySize)}`;
    const body = result.reservationStatus === 'CONFIRMED'
      ? el('div', {}, el('p', {}, when))
      : el('div', {},
          el('p', {}, when),
          el('p', {}, '현재 대기 순번: ', el('b', {}, `${result.waitingPosition}번째`)),
          el('p', { class: 'muted' }, '앞선 확정 예약이 취소되면 대기 순서대로 자동으로 확정됩니다.'));
    const choice = await modal(result.message, body,
      [{ label: '닫기', value: false }, { label: '예약 내역 보기', class: 'primary', value: true }]);
    if (choice) { location.href = '/my.html'; return; }
  } catch (e) {
    if (e.status === 401) { Auth.goLogin(); return; }
    // 동시에 마지막 자리가 나간 경우도 서버가 대기로 처리하므로, 실패는 위 코드들 또는 네트워크 오류
    await modal('예약하지 못했습니다', el('p', {}, RESERVE_ERRORS[e.code] || e.message),
      [{ label: '목록 새로 고침', class: 'primary', value: true }]);
  }
  onDone();
}
