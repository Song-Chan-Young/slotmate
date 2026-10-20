-- 주요 조회의 실행 계획 — 앱이 실제로 보내는 SQL(Hibernate 로그)에 값만 넣음. slotmate_perf 에서 실행
SET @member = (SELECT user_id FROM users WHERE display_name = '테스트회원' LIMIT 1);

SELECT '1 회원 수업 찾기 (하루)' AS q;
EXPLAIN SELECT cs.session_id FROM class_sessions cs JOIN programs p ON p.program_id = cs.program_id
LEFT JOIN instructors i ON i.instructor_id = cs.instructor_id
WHERE cs.start_at >= '2026-10-21' AND cs.start_at < '2026-10-22' AND cs.session_status <> 'DRAFT'
ORDER BY cs.start_at, cs.session_id LIMIT 100;

SELECT '2 관리자 주간 시간표' AS q;
EXPLAIN SELECT cs.session_id FROM class_sessions cs JOIN programs p ON p.program_id = cs.program_id
LEFT JOIN instructors i ON i.instructor_id = cs.instructor_id
WHERE cs.start_at >= '2026-10-19' AND cs.start_at < '2026-10-26'
ORDER BY cs.start_at, cs.session_id LIMIT 500;

SELECT '3 회차별·상태별 건수' AS q;
EXPLAIN SELECT session_id, reservation_status, COUNT(*) FROM reservations
WHERE session_id IN (SELECT session_id FROM class_sessions WHERE start_at >= '2026-10-21' AND start_at < '2026-10-22')
GROUP BY session_id, reservation_status;

SELECT '4 내 예약' AS q;
EXPLAIN SELECT r.reservation_id FROM reservations r JOIN class_sessions s ON s.session_id = r.session_id
WHERE r.user_id = @member ORDER BY s.start_at DESC, r.reservation_id DESC LIMIT 20;

SELECT '5 대기 순번 (일괄)' AS q;
EXPLAIN SELECT r.reservation_id, (SELECT COUNT(*) FROM reservations w WHERE w.session_id = r.session_id
  AND w.reservation_status = 'WAITING'
  AND (w.requested_at < r.requested_at OR (w.requested_at = r.requested_at AND w.reservation_id <= r.reservation_id)))
FROM reservations r WHERE r.reservation_id IN (SELECT reservation_id FROM reservations WHERE user_id = @member AND reservation_status = 'WAITING');

SELECT '6 알림 목록' AS q;
EXPLAIN SELECT n.notification_id FROM notifications n LEFT JOIN reservations r ON r.reservation_id = n.reservation_id
LEFT JOIN class_sessions s ON s.session_id = r.session_id
WHERE n.user_id = @member ORDER BY n.created_at DESC, n.notification_id DESC LIMIT 20;

SELECT '7 읽지 않은 알림 수' AS q;
EXPLAIN SELECT COUNT(*) FROM notifications WHERE user_id = @member AND is_read = 0;

SELECT '8 관리자 회원 목록' AS q;
EXPLAIN SELECT user_id FROM users WHERE role = 'MEMBER' ORDER BY created_at DESC, user_id DESC LIMIT 20;
