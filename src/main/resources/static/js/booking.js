// 회원 — 수업 카드 버튼·배지 규칙(화면 설계서 M01)과 예약 신청 결과(M04)

/** bookingAction → [배지 문구, 배지 색, 버튼 문구, 버튼 스타일(null 이면 비활성)] */
function bookingView(item) {
  switch (item.bookingAction) {
    case 'BOOKABLE': return ['예약 가능', 'ok', '예약하기', 'primary'];
    case 'WAITLISTABLE': return ['대기 신청 가능', 'wait', '대기 신청', 'wait'];
    case 'ALREADY_CONFIRMED': return ['예약 확정', 'ok', '예약 완료', null];
    case 'ALREADY_WAITING': return ['대기 중', 'wait', `대기 중 · ${item.myWaitingPosition}번째`, null];
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
};

async function reserve(item, button, onDone) {
  if (!Auth.token()) { Auth.goLogin(); return; }
  button.disabled = true;
  try {
    const result = await api(`/sessions/${item.sessionId}/reservations`, { method: 'POST' });
    const when = `${prettyDate(ymdOf(item.startAt))} ${hhmm(item.startAt)} · ${item.programName}`;
    const body = result.reservationStatus === 'CONFIRMED'
      ? el('div', {}, el('p', {}, when))
      : el('div', {},
          el('p', {}, when),
          el('p', {}, '현재 대기 순번: ', el('b', {}, `${result.waitingPosition}번째`)),
          el('p', { class: 'muted' }, '앞선 확정 예약이 취소되면 대기 순서대로 자동으로 확정됩니다.'));
    await modal(result.message, body);
  } catch (e) {
    if (e.status === 401) { Auth.goLogin(); return; }
    // 동시에 마지막 자리가 나간 경우도 서버가 대기로 처리하므로, 실패는 위 코드들 또는 네트워크 오류
    await modal('예약하지 못했습니다', el('p', {}, RESERVE_ERRORS[e.code] || e.message),
      [{ label: '목록 새로 고침', class: 'primary', value: true }]);
  }
  onDone();
}
