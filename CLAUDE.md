# SlotMate — 작업 인계

설계 문서는 `docs/` (요구사항 v0.1 · 화면 v0.2 · DB v0.2 · API v0.1 · 로드맵)

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
