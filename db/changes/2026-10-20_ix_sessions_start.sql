-- 이미 만든 DB 에 적용 (새 볼륨은 db/init/01_schema.sql 에 포함돼 있어 필요 없음)
-- docker exec -i slotmate-mariadb mariadb -u<계정> -p<비밀번호> slotmate < db/changes/2026-10-20_ix_sessions_start.sql
ALTER TABLE class_sessions ADD INDEX ix_sessions_start (start_at);
