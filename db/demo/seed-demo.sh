#!/bin/zsh
# 데모 데이터 시드 — 화면 캡처·시연용 (macOS·Linux, zsh·curl·jq·docker 필요)
#   1. 01_demo_data.sql 로 강사·프로그램·이번 주 월요일부터 오늘+13일까지 회차를 다시 만듦 (기존 회차·예약·알림 삭제)
#   2. 데모 회원 10명을 가입시키고, 지난 회차 예약은 02_past_reservations.sql 로 넣음
#   3. 앞으로의 회차는 실제 API 로 예약·대기·취소·자동 승격을 만듦
#      → 이력·알림이 서비스 로직대로 쌓임
# 사용: 앱 실행(./gradlew bootRun) 후 프로젝트 루트에서 ./db/demo/seed-demo.sh
set -euo pipefail

ROOT=${0:A:h:h:h}
BASE=${SLOTMATE_URL:-http://localhost:8080}
set -a; source "$ROOT/.env"; set +a

sql() {
  docker exec -i slotmate-mariadb mariadb -N -B -u"$DB_USERNAME" -p"$DB_PASSWORD" "${DB_NAME:-slotmate}" "$@"
}

curl -sf -o /dev/null "$BASE/login.html" || { echo "앱이 $BASE 에서 실행 중이 아님"; exit 1; }

echo "1/3 회차 데이터"
sql < "$ROOT/db/demo/01_demo_data.sql"

echo "2/3 데모 회원"
# 데모 회원은 매번 지우고 다시 만들기 때문에 비밀번호는 실행마다 새로 만듦(로그인 안 씀)
DEMO_PASSWORD=$(openssl rand -hex 12)
NAMES=(김지우 이도현 박하은 최서윤 정민준 강예린 조현우 윤지아 장시우 임수아)
typeset -a TOKENS
login() {
  curl -sf -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg e "$1" --arg p "$2" '{email:$e,password:$p}')" | jq -r .accessToken
}
for i in {1..10}; do
  email=$(printf 'member%02d@demo.slotmate.dev' $i)
  curl -sf -o /dev/null -X POST "$BASE/api/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg n "${NAMES[$i]}" --arg e "$email" --arg p "$DEMO_PASSWORD" '{displayName:$n,email:$e,password:$p}')"
  TOKENS[$i]=$(login "$email" "$DEMO_PASSWORD")
done
ME=$(login "$MEMBER_EMAIL" "$MEMBER_PASSWORD")
sql < "$ROOT/db/demo/02_past_reservations.sql"

# reserve <토큰> <회차 id> → 예약 id (실패하면 빈 값)
reserve() {
  curl -s -X POST "$BASE/api/v1/sessions/$2/reservations" -H "Authorization: Bearer $1" | jq -r '.reservationId // empty'
}
cancel() {
  curl -sf -o /dev/null -X DELETE "$BASE/api/v1/reservations/$2" -H "Authorization: Bearer $1"
}
# first_session <WHERE 조건> → 조건에 맞는 가장 이른 OPEN 회차 id
first_session() {
  sql -e "SELECT session_id FROM class_sessions WHERE session_status = 'OPEN' AND start_at > NOW() AND $1 ORDER BY start_at LIMIT 1"
}

echo "3/3 예약·대기·취소·승격"
typeset -a SCRIPTED

# 내일 기구 필라테스(정원 4): 4명 확정 → 테스트 회원 대기 1번 → 1명 취소 → 테스트 회원 자동 승격
s=$(first_session "program_id = 4 AND DATE(start_at) = CURDATE() + INTERVAL 1 DAY")
if [[ -n $s ]]; then
  SCRIPTED+=$s
  r2=''
  for i in 1 2 3 4; do
    r=$(reserve ${TOKENS[$i]} $s)
    if [[ $i == 2 ]]; then r2=$r; fi
  done
  reserve $ME $s > /dev/null
  reserve ${TOKENS[5]} $s > /dev/null
  if [[ -n $r2 ]]; then cancel ${TOKENS[2]} $r2; fi
  reserve ${TOKENS[6]} $s > /dev/null
fi

# 모레 이후 기구 필라테스: 가득 찬 회차에 테스트 회원 대기 유지
s=$(first_session "program_id = 4 AND DATE(start_at) >= CURDATE() + INTERVAL 3 DAY")
if [[ -n $s ]]; then
  SCRIPTED+=$s
  for i in 7 8 9 10; do reserve ${TOKENS[$i]} $s > /dev/null; done
  reserve ${TOKENS[1]} $s > /dev/null
  reserve $ME $s > /dev/null
fi

# 오늘 남은 단체 수업 하나 · 내일 이후 요가 하나: 테스트 회원 확정
for cond in "capacity >= 8 AND DATE(start_at) = CURDATE()" "program_id IN (1, 2) AND DATE(start_at) >= CURDATE() + INTERVAL 1 DAY"; do
  s=$(first_session "$cond")
  if [[ -n $s ]]; then reserve $ME $s > /dev/null; fi
done

# 예약했다가 취소한 수업
s=$(first_session "program_id IN (3, 10) AND DATE(start_at) >= CURDATE() + INTERVAL 2 DAY")
if [[ -n $s ]]; then
  r=$(reserve $ME $s)
  if [[ -n $r ]]; then cancel $ME $r; fi
fi

# 나머지 7일치 회차: 회차마다 정해진 수만큼 데모 회원 예약 (정원 작은 수업은 대기까지)
for row in ${(f)"$(sql -e "SELECT session_id, capacity FROM class_sessions WHERE session_status = 'OPEN' AND start_at > NOW() AND start_at < CURDATE() + INTERVAL 7 DAY")"}; do
  sid=${row%%$'\t'*}; cap=${row##*$'\t'}
  if (( ${SCRIPTED[(Ie)$sid]} )); then continue; fi
  max=$(( cap + 2 < 10 ? cap + 2 : 10 ))
  n=$(( sid * 7 % (max + 1) ))
  for (( i = 1; i <= n; i++ )); do
    reserve ${TOKENS[$(( (sid + i) % 10 + 1 ))]} $sid > /dev/null
  done
done

sql -t -e "SELECT session_status, COUNT(*) AS sessions FROM class_sessions GROUP BY session_status;
           SELECT reservation_status, COUNT(*) AS reservations FROM reservations GROUP BY reservation_status;
           SELECT event_type, COUNT(*) AS histories FROM reservation_histories GROUP BY event_type;
           SELECT COUNT(*) AS notifications FROM notifications;"
echo "끝"
