# SlotMate — 작업 인계

설계 문서는 `docs/` (요구사항 v0.1 · 화면 v0.2 · DB v0.2 · API v0.2 · 로드맵). 구현에서 정한 것은 API v0.2 13절

## 작업 방식 (회사 PC 개인 규칙 중 이 프로젝트에 맞는 것)

| 항목 | 규칙 |
|---|---|
| 호칭 | 「주임님」 또는 「찬영님」. ⛔ 「사용자」·「개발자」 |
| 답변 | 짧게, 핵심만 한 줄씩. 한 것만 말함 — 「~하셔야 합니다」 당부 안 붙임 |
| 추정 금지 | 소스·문서로 확인한 것만 사실로 씀. 모르면 묻거나 `(미확인)`. 설계서 문구 넓게 해석 안 함 |
| 요청 밖 | 요청에 없는 동작(메시지·검증·예외처리)은 넣지 않고 먼저 물음 |
| 「됐습니다」 | 명령 성공 ≠ 해결. 확인한 뒤 말함. **못 한 것부터** 말함. 「0건」은 한 번 의심 |
| 시각 | 날짜·시각은 `date` 로 재서 씀. 어림 금지 |
| 파일 고칠 때 | **Edit** (diff 보이게). Write 는 새 파일만. 전체 재작성은 먼저 알림 |
| 파일이 다르면 | 내가 아는 것과 다르면 덮지 않음 — 「기대한 것 / 지금 있는 것」 나란히 놓고 물음 |
| 주임님이 고친 것 | 손대야 하면 먼저 물음. 하지 말라면 안 함 |
| 문서 | 표·짧은 줄, 한 항목 한 줄, 종결어미 줄임 |
| 코드 수정 | 기존 코드는 지우고 고침 — 원본 주석 안 남김(git 이력으로 되돌림) |
| 비밀정보 | 비밀번호·키는 `.env` 에만. 커밋·문서·로그에 안 넣음. 회사 코드·데이터 안 넣음 |
| 커밋·push | 주임님이 시킬 때만 |

## 진행 상태 (2026-10-20, 집 macOS)

| 단계 | 상태 |
|---|---|
| 3 초기화 | macOS 에서 빌드·DB 기동·`ddl-auto=validate` 확인. Windows 재확인은 안 함 |
| 4 기본 기능 | 인증·권한 · 관리자 프로그램/회차 · 회원 조회 · 예약 생성 · 내 예약(7.2) · 취소(7.3) · 회차별 예약자(9.5) 끝 |
| 5 정합성 | 자동 승격 · 이력 · 알림(8절) · 동시 요청 테스트 끝. 테스트 72개 통과 |
| 6 품질·배포 | k6 부하 테스트(`loadtest/`) — 동시 취소 승격 버그 찾아 고침. Dockerfile·compose `app` 프로필·GitHub Actions CI. AWS 배포 끝(2026-10-20, 커밋 `35cd434` 빌드) — 아래 「배포」. 쿼리 성능 점검(`loadtest/perf/`) — 대기 순번 N+1 수정, `ix_sessions_start` 추가(운영 RDS 미적용) |

**한 것**
- Boot 4.1.1 · Java 21 · Gradle(Groovy) · Web·Validation·Security·OAuth2 Resource Server·JPA·MariaDB·springdoc 3.1.1
- `compose.yaml` MariaDB 11.4 + `db/init/01_schema.sql`(DB 설계서 v0.2 DDL 그대로)
- `ddl-auto=validate` — 테이블은 schema.sql 이 만들고 JPA 는 대조만
- 인증: JWT HS256, `/api/v1/auth/signup`·`login`. 관리자 계정은 기동 시 `.env` 값으로 생성
- 관리자: `/api/v1/admin/programs`·`/api/v1/admin/sessions` (9.1~9.4)
- 회원: `GET /api/v1/programs`·`/api/v1/sessions` (6.1~6.4) · `POST /api/v1/sessions/{id}/reservations` (7.1) · `GET /api/v1/my/reservations` (7.2) · `DELETE /api/v1/reservations/{id}` (7.3)
- 관리자 회차별 예약자: `GET /api/v1/admin/sessions/{id}/reservations` (9.5)
- 알림: `GET /api/v1/my/notifications` · `PATCH …/{id}/read` (8절)
- 명세 밖 추가 조회: `GET /api/v1/admin/instructors` · `GET /api/v1/admin/members` (API 13.8)
- 화면(static): 회원 홈·상세·내 예약·알림 · 관리자 대시보드·시간표·프로그램·회차별 예약·회원
- 오류 응답 `{code, message, fieldErrors}` — `common/error/ErrorCode`
- 테스트는 로컬 MariaDB 에 붙어 돌고 각 테스트 롤백 (`support/ApiTestSupport`)
- 데모 데이터: 앱 실행 중 `./db/demo/seed-demo.sh` — 회차·예약·알림을 지우고 이번 주 월요일~오늘+13일로 다시 만듦 (macOS·zsh·jq)

**정한 것**
- 화면은 나중. API 먼저(Swagger 로 확인)
- DB 는 Docker 로 MariaDB
- 범위는 단계별로 하나씩
- API 세부 결정은 `docs/05_API_SPEC_v0.2.md` 13절

## 다른 PC 에서 이어서 할 때

1. `git pull` → `cp .env.example .env` → 값 채움 (`JWT_SECRET` 32바이트 이상, `ADMIN_EMAIL`·`ADMIN_PASSWORD`)
2. `docker compose up -d` → 테이블 7개 확인. 스키마가 바뀌었으면 `docker compose down -v` 후 다시 `up`
3. `./gradlew build` → 테스트 통과 확인 → `./gradlew bootRun`
4. Swagger: http://localhost:8080/swagger-ui/index.html → `auth/login` 으로 토큰 받아 Authorize

macOS(집) 메모
- Docker 는 Docker Desktop 대신 colima (`brew install colima docker docker-compose`, `colima start`)
- 기본 java 가 26 이라 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` 후 gradlew 실행
- 재부팅 후: `colima start` → `docker compose up -d` → `./gradlew bootRun`

## 배포 (AWS 서울, 무료 플랜 크레딧)

| 항목 | 값 |
|---|---|
| 주소 | https://43-200-0-75.sslip.io (탄력적 IP 43.200.0.75) |
| 앱 | EC2 `slotmate-app` t4g.micro · AL2023 · `/opt/slotmate/app.jar` · systemd `slotmate` · 환경 파일 `/etc/slotmate/env` |
| DB | RDS `slotmate-db` MariaDB 11.4.13 · 파라미터 그룹 `slotmate-mariadb114`(time_zone Asia/Seoul, utf8mb4) · 퍼블릭 접근 끔 |
| 웹 | nginx → 127.0.0.1:8080 · certbot 인증서 · `certbot-renew.timer` |
| 접속 정보 | 집 맥북 `~/.slotmate-aws/env`(DB·관리자 비밀번호, 보안 그룹 ID) · SSH 키 `~/.ssh/slotmate.pem` · SSH 는 집 IP 만 허용 |
| AWS CLI | IAM 사용자 `slotmate-cli` · 리전 `ap-northeast-2` |
| 요금 | 무료 플랜(2027-04-20 까지) · 크레딧 차감만 · 예산 알림 `zero-spend` |

- 재배포: `./gradlew bootJar` → jar 를 `/opt/slotmate/app.jar` 로 올림 → `sudo systemctl restart slotmate` (CI 자동 배포는 안 함)
- 데모 데이터 공개 계정: `demo@slotmate.dev` (README 에 공개). 관리자 계정은 비공개
- 데모 데이터 다시 넣기: `seed-demo.sh` 는 로컬 docker 전용 — 운영은 `sql()` 을 SSH 경유로 바꾼 사본으로 실행함(저장소에 없음)

## 알아둘 것

- 커밋 작성자는 저장소 로컬 설정(개인 계정). PC 마다 `git config user.name/user.email` 확인
- 요구사항 11절 · API 12절의 미확정 항목은 아직 그대로 (12절에 v0.2 에서 새로 생긴 미정 항목 추가)
- 강사 관리 API 없음 — 강사는 DB 에 직접 INSERT
