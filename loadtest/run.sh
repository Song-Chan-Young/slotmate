#!/bin/zsh
# 동시 예약·취소 부하 테스트 실행 — 앱 실행 중, k6 필요 (brew install k6)
# 사용: ./loadtest/run.sh [회원 수] [정원]   예) ./loadtest/run.sh 500 10
set -euo pipefail

ROOT=${0:A:h:h}
set -a; source "$ROOT/.env"; set +a

k6 run --quiet \
  -e BASE_URL="${SLOTMATE_URL:-http://localhost:8080}" \
  -e ADMIN_EMAIL="$ADMIN_EMAIL" -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  -e USERS="${1:-200}" -e CAPACITY="${2:-4}" \
  "$ROOT/loadtest/reservation-race.js"
