#!/bin/zsh
# 쿼리 성능 측정 환경 — 별도 DB slotmate_perf 를 만들고 앱을 8082 포트로 띄운 뒤 대량 데이터를 넣음
# 데모 DB(slotmate)는 건드리지 않음. 사용: ./loadtest/perf/setup.sh   (끝나면 앱은 계속 떠 있음, 로그 build/perf-app.log)
# SQL 로그를 켜고 띄움 → 요청 한 번에 쿼리가 몇 개 나가는지 셀 수 있음
set -euo pipefail

ROOT=${0:A:h:h:h}
set -a; source "$ROOT/.env"; set +a
PERF_DB=slotmate_perf
PORT=${PERF_PORT:-8082}

root_sql() { docker exec -i slotmate-mariadb mariadb -uroot -p"$DB_ROOT_PASSWORD" "$@"; }

echo "1/4 DB 다시 만들기 ($PERF_DB)"
root_sql -e "DROP DATABASE IF EXISTS $PERF_DB; CREATE DATABASE $PERF_DB;
             GRANT ALL PRIVILEGES ON $PERF_DB.* TO '$DB_USERNAME'@'%';"
root_sql $PERF_DB < "$ROOT/db/init/01_schema.sql"

echo "2/4 앱 기동 (포트 $PORT) — 관리자·테스트회원 생성"
lsof -t -iTCP:$PORT -sTCP:LISTEN | xargs kill 2>/dev/null || true
(cd "$ROOT" && ./gradlew -q bootJar -x test)
(cd "$ROOT" && nohup java -jar build/libs/slotmate-0.0.1-SNAPSHOT.jar --server.port=$PORT \
  --spring.datasource.url=jdbc:mariadb://localhost:${DB_PORT:-3306}/$PERF_DB \
  --logging.level.org.hibernate.SQL=debug > build/perf-app.log 2>&1 &)
for i in {1..60}; do grep -q "Started SlotmateApplication" "$ROOT/build/perf-app.log" 2>/dev/null && break; sleep 1; done

echo "3/4 대량 데이터 (수십 초 걸림)"
root_sql $PERF_DB < "$ROOT/loadtest/perf/01_perf_data.sql" > /dev/null

echo "4/4 건수"
root_sql -t $PERF_DB -e "SELECT (SELECT COUNT(*) FROM users) users, (SELECT COUNT(*) FROM class_sessions) sessions,
  (SELECT COUNT(*) FROM reservations) reservations, (SELECT COUNT(*) FROM reservation_histories) histories,
  (SELECT COUNT(*) FROM notifications) notifications;"
