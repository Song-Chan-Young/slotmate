// 동시 예약·취소 부하 테스트 — 실행: ./loadtest/run.sh (앱 실행 중)
//
// 1. 신청 경합: 회원 USERS 명이 정원 CAPACITY 명 회차에 동시에 신청. 회원마다 같은 요청을 2개 동시에 보냄(따닥)
//    → 확정 = 정원, 나머지는 대기 순번 1..N, 회원당 활성 예약 1건
// 2. 취소 경합: 확정된 회원이 같은 시각에 동시에 취소
//    → 취소한 만큼 가장 먼저 신청한 대기자부터 자동 승격, 확정 수는 다시 정원
// 테스트용 프로그램·회차는 끝나고 비활성·취소 처리. 회원은 다음 seed-demo.sh 실행 때 지워짐

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE}/api/v1`;
const USERS = Number(__ENV.USERS || 200);
const CAPACITY = Number(__ENV.CAPACITY || 4);
const CANCEL_DELAY_MS = 10000;

const reserveCreated = new Counter('reserve_201');
const reserveDuplicate = new Counter('reserve_409_duplicate');
const reserveOther = new Counter('reserve_other');

export const options = {
  setupTimeout: '180s',
  scenarios: {
    race: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '60s' },
  },
  thresholds: {
    checks: ['rate==1'],
    reserve_other: ['count==0'],
    // 응답 시간은 기록용 (신청·취소만 따로 보이게)
    'http_req_duration{name:reserve}': ['p(95)<5000'],
    'http_req_duration{name:cancel}': ['p(95)<5000'],
  },
};

const json = (token) => ({
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
});
const body = (obj) => JSON.stringify(obj);
const auth = (token, tags) => ({ headers: { Authorization: `Bearer ${token}` }, tags });

function login(email, password) {
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email, password }), json());
  if (res.status !== 200) throw new Error(`login ${email} → ${res.status} ${res.body}`);
  return res.json('accessToken');
}

export function setup() {
  const admin = login(__ENV.ADMIN_EMAIL, __ENV.ADMIN_PASSWORD);
  const runId = Date.now();

  const program = http.post(`${API}/admin/programs`, body({
    name: `부하테스트 ${runId}`, type: 'ETC', defaultDurationMinutes: 50, defaultCapacity: CAPACITY }), json(admin)).json();
  const start = new Date(Date.now() + 30 * 24 * 3600 * 1000);
  start.setUTCHours(1, 0, 0, 0); // 10:00 KST
  const end = new Date(start.getTime() + 50 * 60 * 1000);
  const session = http.post(`${API}/admin/sessions`, body({
    programId: program.programId, startAt: start.toISOString(), endAt: end.toISOString(),
    capacity: CAPACITY, initialStatus: 'OPEN' }), json(admin));
  if (session.status !== 201) throw new Error(`session → ${session.status} ${session.body}`);

  // 회원 가입·로그인 (bcrypt 라 50명씩 병렬)
  const password = `load-${runId}-pw`;
  const emails = Array.from({ length: USERS }, (_, i) => `load-${runId}-${String(i + 1).padStart(3, '0')}@demo.slotmate.dev`);
  const tokens = [];
  for (let i = 0; i < USERS; i += 50) {
    const chunk = emails.slice(i, i + 50);
    http.batch(chunk.map((email, j) => ['POST', `${API}/auth/signup`,
      JSON.stringify({ displayName: `부하${i + j + 1}`, email, password }), json()]));
    const logins = http.batch(chunk.map((email) => ['POST', `${API}/auth/login`, JSON.stringify({ email, password }), json()]));
    logins.forEach((r) => tokens.push(r.json('accessToken')));
  }

  return { admin, programId: program.programId, sessionId: session.json('sessionId'), tokens,
    cancelAt: Date.now() + CANCEL_DELAY_MS };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const url = `${API}/sessions/${data.sessionId}/reservations`;
  // 같은 회원이 같은 요청을 동시에 2번
  const responses = http.batch([
    ['POST', url, null, auth(token, { name: 'reserve' })],
    ['POST', url, null, auth(token, { name: 'reserve' })],
  ]);

  let mine = null;
  responses.forEach((r) => {
    if (r.status === 201) { reserveCreated.add(1); mine = r.json(); }
    else if (r.status === 409 && r.json('code') === 'DUPLICATE_ACTIVE_RESERVATION') reserveDuplicate.add(1);
    else { reserveOther.add(1); console.warn(`reserve VU${__VU} → ${r.status} ${r.error || ''} ${r.body || ''}`); }
  });
  check(responses, { '회원당 신청 1건만 성공': (rs) => rs.filter((r) => r.status === 201).length === 1 });

  // 확정된 회원은 같은 시각에 동시에 취소
  if (mine && mine.reservationStatus === 'CONFIRMED') {
    sleep(Math.max(0, data.cancelAt - Date.now()) / 1000);
    const res = http.del(`${API}/reservations/${mine.reservationId}`, null, auth(token, { name: 'cancel' }));
    check(res, { '확정 취소 200': (r) => r.status === 200 });
  }
}

export function teardown(data) {
  const page = http.get(`${API}/admin/sessions/${data.sessionId}/reservations?size=1000`, auth(data.admin)).json();
  const items = page.items; // requestedAt, reservationId 오름차순
  const by = (s) => items.filter((r) => r.reservationStatus === s);
  const confirmed = by('CONFIRMED');
  const waiting = by('WAITING');
  const cancelled = by('CANCELLED');
  const active = items.filter((r) => r.reservationStatus !== 'CANCELLED');
  const promoted = confirmed.filter((r) => r.confirmedAt !== r.requestedAt);

  check(null, {
    '예약 행 = 회원 수 (중복 없음)': () => items.length === USERS
      && new Set(items.map((r) => r.memberEmail)).size === USERS,
    '확정 = 정원': () => confirmed.length === CAPACITY,
    '취소 = 처음 확정자 수': () => cancelled.length === CAPACITY,
    '승격 = 취소 수': () => promoted.length === CAPACITY,
    '승격은 가장 먼저 신청한 대기자부터': () => active.slice(0, CAPACITY).every((r) => r.reservationStatus === 'CONFIRMED'),
    '대기 순번 1..N 연속': () => waiting.every((r, i) => r.waitingPosition === i + 1),
  });
  console.log(JSON.stringify({ users: USERS, capacity: CAPACITY, rows: items.length, confirmed: confirmed.length,
    promoted: promoted.length, waiting: waiting.length, cancelled: cancelled.length }));

  http.patch(`${API}/admin/sessions/${data.sessionId}/status`, body({ status: 'CANCELLED' }), json(data.admin));
  http.patch(`${API}/admin/programs/${data.programId}/active`, body({ active: false }), json(data.admin));
}
