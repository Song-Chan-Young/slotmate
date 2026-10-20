-- 지난(COMPLETED) 회차의 예약 기록 — API 는 지난 회차를 예약할 수 없어서 SQL 로 넣음
-- seed-demo.sh 가 데모 회원을 만든 뒤 실행. 확정 예약과 BOOKED 이력만 넣고 알림은 안 만듦

CREATE TEMPORARY TABLE demo_members AS
SELECT user_id, ROW_NUMBER() OVER (ORDER BY email) AS n
FROM users WHERE email LIKE '%@demo.slotmate.dev';

INSERT INTO reservations (session_id, user_id, reservation_status, requested_at, confirmed_at)
SELECT s.session_id, m.user_id, 'CONFIRMED',
       s.start_at - INTERVAL m.n HOUR - INTERVAL 1 DAY,
       s.start_at - INTERVAL m.n HOUR - INTERVAL 1 DAY
FROM class_sessions s
JOIN demo_members m ON m.n <= (s.session_id * 7) % (LEAST(s.capacity, 10) + 1)
WHERE s.session_status = 'COMPLETED';

INSERT INTO reservation_histories (reservation_id, attempt_no, from_status, to_status, event_type, actor_user_id, changed_at)
SELECT r.reservation_id, 1, NULL, 'CONFIRMED', 'BOOKED', r.user_id, r.requested_at
FROM reservations r
JOIN class_sessions s ON s.session_id = r.session_id
WHERE s.session_status = 'COMPLETED';

DROP TEMPORARY TABLE demo_members;
