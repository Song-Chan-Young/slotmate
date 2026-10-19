# SlotMate — 작업 인계

설계 문서는 `docs/` (요구사항 v0.1 · 화면 v0.2 · DB v0.2 · API v0.1 · 로드맵)

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

## 진행 상태 (2026-10-19, 회사 Windows PC)

| 단계 | 상태 |
|---|---|
| 3 초기화 | 코드 작성 끝 · **빌드·DB 기동 미검증** (JDK 21·Docker 없어서) |
| 4 기본 기능 | 안 함 |

**한 것**
- Boot 4.1.1 · Java 21 · Gradle(Groovy) · Web·Validation·Security·JPA·MariaDB·springdoc 3.1.1
- `compose.yaml` MariaDB 11.4 + `db/init/01_schema.sql`(DB 설계서 v0.2 DDL 그대로)
- `com.slotmate.*` enum 7 · 엔티티 7 (getter 만 있는 뼈대)
- `ddl-auto=none` — 테이블은 schema.sql 이 만듦

**정한 것**
- 화면은 나중. API 먼저(Swagger 로 확인)
- DB 는 Docker 로 MariaDB
- 범위는 단계별로 하나씩

## 집(macOS)에서 이어서 할 일

0. `git clone https://github.com/Song-Chan-Young/slotmate.git` → `cd slotmate` → `claude` (이 파일 자동으로 읽힘)
   - 커밋 작성자: `git config user.name "송찬영"` · `git config user.email "thdcksdud123@gmail.com"`
1. JDK 21 설치 (IntelliJ → Project Structure → SDK → Download JDK → 21)
2. Docker Desktop for Mac 설치 (Apple 칩/Intel 맞는 것)
3. `cp .env.example .env` → 비밀번호 채움 (`.env` 는 git 제외, PC 마다 따로)
4. `docker compose up -d` → 테이블 7개 생성 확인
5. `./gradlew build` — 컴파일·`contextLoads` 테스트 통과 확인
6. 확인되면 `ddl-auto=validate` 로 바꿔 엔티티↔테이블 대조
7. 그다음 단계 4 (인증·권한부터)

## 알아둘 것

- Spring Security 기본값이라 지금은 모든 API·Swagger 가 로그인 요구
- 커밋 작성자는 저장소 로컬 설정(개인 계정). 맥에서도 `git config user.name/user.email` 확인
- 요구사항 11절 · API 12절의 미확정 항목은 아직 그대로
