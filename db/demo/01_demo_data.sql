-- 데모 데이터 — 강사·프로그램·회차를 이번 주 월요일부터 오늘+13일까지 다시 만듦
-- 카테고리: 운동(1~11) · 원데이클래스(12~14) · 놀거리(15~17). 클래스·놀거리는 일행 예약(max_party_size) 가능
-- 예약·이력·알림·데모 회원도 지움 (관리자·.env 테스트 회원·직접 가입한 회원은 남김)
-- 예약은 db/demo/seed-demo.sh 가 API 로 만듦

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE notifications;
TRUNCATE TABLE reservation_histories;
TRUNCATE TABLE reservations;
TRUNCATE TABLE class_sessions;
TRUNCATE TABLE programs;
TRUNCATE TABLE instructors;
SET FOREIGN_KEY_CHECKS = 1;

DELETE FROM users WHERE email LIKE '%@demo.slotmate.dev';

INSERT INTO instructors (instructor_id, name, specialty) VALUES
    (1, '김하늘', '요가'),
    (2, '박서연', '필라테스'),
    (3, '최민호', 'GX·스피닝'),
    (4, '정우진', 'PT·웨이트'),
    (5, '윤소라', '스트레칭·요가'),
    (6, '한도윤', '클라이밍'),
    (7, '오지훈', '러닝'),
    (8, '이수빈', '수영'),
    (9, '흙과불 공방', '도예'),
    (10, '셰프 강민', '이탈리안 요리'),
    (11, '아틀리에 윤', '유화'),
    (12, '가이드 서준', '서울 도보 투어'),
    (13, '한강레저', '카약·SUP'),
    (14, '산길 가이드', '트레킹');

INSERT INTO programs (program_id, program_type, name, description, default_duration_minutes, default_capacity, max_party_size) VALUES
    (1,  'YOGA',    '기초 요가',       '호흡과 기본 자세로 몸을 푸는 입문 요가. 처음 오시는 분도 따라 할 수 있어요.', 50, 12, 1),
    (2,  'YOGA',    '파워 빈야사',     '동작을 이어 가며 근력과 유연성을 함께 기르는 중급 요가.', 60, 10, 1),
    (3,  'PILATES', '매트 필라테스',   '매트 위에서 코어를 단련하는 소그룹 필라테스.', 50, 8, 1),
    (4,  'PILATES', '기구 필라테스',   '리포머로 자세를 교정하는 4인 정원 수업.', 50, 4, 1),
    (5,  'GX',      '스피닝',          '음악에 맞춰 달리는 고강도 실내 사이클.', 45, 15, 1),
    (6,  'GX',      '줌바',            '라틴 음악에 맞춘 댄스 유산소.', 50, 20, 1),
    (7,  'PT',      'PT 1:1',          '트레이너와 1:1로 진행하는 맞춤 웨이트 트레이닝.', 50, 1, 1),
    (8,  'ETC',     '스트레칭 클래스', '하루를 마무리하는 전신 스트레칭.', 40, 15, 1),
    (9,  'ETC',     '실내 클라이밍',   '볼더링 기초와 안전 수칙을 배우는 입문 수업.', 90, 8, 1),
    (10, 'ETC',     '수영 기초',       '호흡과 발차기부터 배우는 성인 초급반.', 50, 10, 1),
    (11, 'ETC',     '러닝 크루',       '주말 아침 5km 함께 달리기.', 60, 20, 1),
    (12, 'CRAFT',      '도자기 물레 원데이',  '물레로 컵·그릇을 직접 빚어요. 초벌·재벌 후 2주 뒤 택배 발송.', 120, 8, 4),
    (13, 'COOKING',    '수제 파스타 쿠킹',    '반죽부터 소스까지 2가지 파스타를 만들고 함께 먹어요.', 120, 10, 4),
    (14, 'ART',        '유화 드로잉 원데이',  '초보도 완성하는 소품 유화. 재료 모두 제공.', 150, 8, 2),
    (15, 'TOUR',       '서울 야경 가이드 투어', '광화문에서 청계천·을지로까지 밤 산책 2시간.', 120, 12, 6),
    (16, 'EXPERIENCE', '한강 카약 체험',      '구명조끼·장비 대여 포함. 수영 못해도 괜찮아요.', 90, 10, 4),
    (17, 'LEISURE',    '북한산 트레킹',       '백운대 코스 반나절 트레킹. 간식 제공.', 240, 15, 6);

-- 주간 시간표 틀. dows 는 WEEKDAY() 값(0=월 … 6=일)을 이은 문자열
CREATE TEMPORARY TABLE demo_slots (
    program_id BIGINT, instructor_id BIGINT, start_time TIME, dows VARCHAR(7), location VARCHAR(150)
);
INSERT INTO demo_slots VALUES
    (11, 7, '06:30', '56',      '한강 반포지구 집결'),
    (5,  3, '07:00', '024',     'GX룸 B'),
    (3,  2, '07:00', '13',      '3층 필라테스룸'),
    (1,  1, '09:00', '01234',   '2층 요가룸'),
    (4,  2, '10:00', '0123456', '3층 필라테스룸'),
    (8,  5, '11:00', '56',      '2층 요가룸'),
    (7,  4, '12:00', '01234',   '웨이트존'),
    (10, 8, '14:00', '135',     '지하 수영장'),
    (7,  4, '15:00', '0123456', '웨이트존'),
    (9,  6, '18:00', '024',     'B1 클라이밍월'),
    (2,  1, '19:30', '024',     '2층 요가룸'),
    (6,  3, '19:00', '13',      'GX룸 A'),
    (5,  3, '20:00', '13',      'GX룸 B'),
    (3,  2, '20:30', '024',     '3층 필라테스룸'),
    (8,  5, '21:30', '01234',   '2층 요가룸'),
    (12, 9,  '14:00', '56',      '성수 흙과불 공방'),
    (12, 9,  '19:00', '24',      '성수 흙과불 공방'),
    (13, 10, '11:00', '5',       '연남 쿠킹 스튜디오'),
    (13, 10, '19:30', '13',      '연남 쿠킹 스튜디오'),
    (14, 11, '15:00', '6',       '을지로 아틀리에'),
    (15, 12, '19:30', '3456',    '광화문 광장 집결'),
    (16, 13, '10:00', '56',      '뚝섬 한강공원'),
    (16, 13, '16:30', '24',      '뚝섬 한강공원'),
    (17, 14, '08:00', '56',      '북한산성 탐방지원센터');

INSERT INTO class_sessions (program_id, instructor_id, start_at, end_at, capacity, location, session_status, created_by_user_id)
WITH RECURSIVE days AS (
    SELECT CURDATE() - INTERVAL WEEKDAY(CURDATE()) DAY AS d
    UNION ALL
    SELECT d + INTERVAL 1 DAY FROM days WHERE d < CURDATE() + INTERVAL 13 DAY
)
SELECT s.program_id, s.instructor_id,
       TIMESTAMP(days.d, s.start_time),
       TIMESTAMP(days.d, s.start_time) + INTERVAL p.default_duration_minutes MINUTE,
       p.default_capacity, s.location,
       IF(TIMESTAMP(days.d, s.start_time) < NOW(), 'COMPLETED', 'OPEN'),
       (SELECT user_id FROM users WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1)
FROM days
JOIN demo_slots s ON INSTR(s.dows, WEEKDAY(days.d)) > 0
JOIN programs p ON p.program_id = s.program_id
ORDER BY days.d, s.start_time;

-- 상태 다양화: 마지막 주 일부는 준비 중, 다음 주 수영 한 회차는 강사 사정으로 취소
UPDATE class_sessions SET session_status = 'DRAFT'
WHERE start_at >= CURDATE() + INTERVAL 12 DAY AND program_id IN (2, 9);
UPDATE class_sessions SET session_status = 'CANCELLED'
WHERE start_at >= CURDATE() + INTERVAL 7 DAY AND program_id = 10
ORDER BY start_at LIMIT 1;

DROP TEMPORARY TABLE demo_slots;
