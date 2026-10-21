-- 여러 명 예약 (docs/06_CATEGORY_PARTY_BOOKING_v0.1.md 3.1). 기본값 1 이라 기존 데이터는 1명 예약 그대로
-- 새 볼륨은 db/init/01_schema.sql 에 포함돼 있어 필요 없음
ALTER TABLE programs ADD COLUMN max_party_size SMALLINT NOT NULL DEFAULT 1 AFTER default_capacity;
ALTER TABLE reservations ADD COLUMN party_size INT NOT NULL DEFAULT 1 AFTER reservation_status;
