-- 쿼리 성능 측정용 대량 데이터 — 별도 DB(slotmate_perf)에서만 실행 (loadtest/perf/setup.sh)
-- 회원 1만 · 회차 3년치(2025~2027, 하루 15개) · 예약은 회차마다 0~정원+2건 · 이력·알림은 예약마다 1건

-- 관리자·테스트회원은 앱 기동 때 이미 만들어져 있음 (AccountInitializer)
INSERT INTO users (email, password_hash, display_name, role)
SELECT CONCAT('perf', seq, '@perf.local'), 'x', CONCAT('회원', seq), 'MEMBER' FROM seq_1_to_10000;
SET @first_user = (SELECT MIN(user_id) FROM users WHERE email LIKE '%@perf.local');
SET @member = (SELECT user_id FROM users WHERE display_name = '테스트회원' LIMIT 1);

INSERT INTO instructors (instructor_id, name, specialty) VALUES
    (1, '김하늘', '요가'), (2, '박서연', '필라테스'), (3, '최민호', 'GX'), (4, '정우진', 'PT'),
    (5, '윤소라', '스트레칭'), (6, '한도윤', '클라이밍'), (7, '오지훈', '러닝'), (8, '이수빈', '수영');

INSERT INTO programs (program_id, program_type, name, default_duration_minutes, default_capacity) VALUES
    (1, 'YOGA', '기초 요가', 50, 12), (2, 'YOGA', '파워 빈야사', 60, 10), (3, 'PILATES', '매트 필라테스', 50, 8),
    (4, 'PILATES', '기구 필라테스', 50, 4), (5, 'GX', '스피닝', 45, 15), (6, 'GX', '줌바', 50, 20),
    (7, 'PT', 'PT 1:1', 50, 1), (8, 'ETC', '스트레칭 클래스', 40, 15), (9, 'ETC', '실내 클라이밍', 90, 8),
    (10, 'ETC', '수영 기초', 50, 10), (11, 'ETC', '러닝 크루', 60, 20);

-- 하루 15개 시간대 × 1,095일
INSERT INTO class_sessions (program_id, instructor_id, start_at, end_at, capacity, location, session_status, created_by_user_id)
SELECT p.program_id, LEAST(p.program_id, 8),
       TIMESTAMP(DATE('2025-01-01') + INTERVAL d.seq DAY, MAKETIME(6 + h.seq, 0, 0)),
       TIMESTAMP(DATE('2025-01-01') + INTERVAL d.seq DAY, MAKETIME(6 + h.seq, 0, 0)) + INTERVAL p.default_duration_minutes MINUTE,
       p.default_capacity, '본관',
       IF(TIMESTAMP(DATE('2025-01-01') + INTERVAL d.seq DAY, MAKETIME(6 + h.seq, 0, 0)) < NOW(), 'COMPLETED', 'OPEN'),
       (SELECT user_id FROM users WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1)
FROM seq_0_to_1094 d
JOIN seq_0_to_14 h
JOIN programs p ON p.program_id = (d.seq + h.seq) % 11 + 1
ORDER BY d.seq, h.seq;

-- 회차마다 k = id*7 % (정원+3) 명. 정원까지 확정, 넘으면 대기. 10건 중 1건은 취소
INSERT INTO reservations (session_id, user_id, reservation_status, requested_at, confirmed_at, cancelled_at)
SELECT s.session_id, (s.session_id * 31 + n.seq) % 10000 + @first_user,
       CASE WHEN (s.session_id + n.seq) % 10 = 0 THEN 'CANCELLED'
            WHEN n.seq < s.capacity THEN 'CONFIRMED' ELSE 'WAITING' END,
       s.start_at - INTERVAL 2 DAY + INTERVAL n.seq MINUTE,
       IF(n.seq < s.capacity, s.start_at - INTERVAL 2 DAY + INTERVAL n.seq MINUTE, NULL),
       IF((s.session_id + n.seq) % 10 = 0, s.start_at - INTERVAL 1 DAY, NULL)
FROM class_sessions s
JOIN seq_0_to_22 n ON n.seq < (s.session_id * 7) % (s.capacity + 3);

-- 테스트회원: 회차 50개 중 1개꼴(약 330건). 앞으로의 회차 중 가득 찬 곳은 대기 → 내 예약 목록에 대기 순번 계산이 섞임
INSERT INTO reservations (session_id, user_id, reservation_status, requested_at, confirmed_at)
SELECT s.session_id, @member,
       IF(s.start_at > NOW() AND (s.session_id * 7) % (s.capacity + 3) >= s.capacity, 'WAITING', 'CONFIRMED'),
       s.start_at - INTERVAL 1 DAY,
       IF(s.start_at > NOW() AND (s.session_id * 7) % (s.capacity + 3) >= s.capacity, NULL, s.start_at - INTERVAL 1 DAY)
FROM class_sessions s WHERE s.session_id % 50 = 0;

INSERT INTO reservation_histories (reservation_id, attempt_no, to_status, event_type, actor_user_id, changed_at)
SELECT reservation_id, 1, reservation_status,
       CASE reservation_status WHEN 'WAITING' THEN 'WAITLISTED' WHEN 'CANCELLED' THEN 'CANCELLED' ELSE 'BOOKED' END,
       user_id, requested_at
FROM reservations;

INSERT INTO notifications (user_id, reservation_id, notification_type, title, message, is_read, created_at)
SELECT user_id, reservation_id,
       CASE reservation_status WHEN 'WAITING' THEN 'WAITLISTED' WHEN 'CANCELLED' THEN 'CANCELLED' ELSE 'BOOKING_CONFIRMED' END,
       '예약 알림', '성능 측정용', reservation_id % 3 = 0, requested_at
FROM reservations;

ANALYZE TABLE users, class_sessions, reservations, reservation_histories, notifications;
