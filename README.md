# SlotMate (가칭)

예약·대기 통합 관리 웹 서비스

> 한 줄 소개: 피트니스 센터의 PT·요가·GX 시간표를 관리하고, 예약 취소가 발생하면 대기자를 자동 승격하는 서비스.

## 프로젝트 방향

- 목표: Java/Spring 백엔드 경력직 이직을 위한 실무형 포트폴리오
- 핵심 역량: 예약 상태 관리, 트랜잭션, 동시성 제어, 데이터 정합성, 테스트, 예외 처리, SQL 성능
- 개발 방식: 요구사항 → 화면 흐름 → DB 설계 → API 설계 → 구현 → 테스트 → 배포·문서화
- 저장소 이름 제안: `slotmate`

## 1차 버전 범위

- 회원 가입 및 로그인
- 프로그램·회차 목록 조회
- 회차별 예약 및 예약 취소
- 정원 초과 시 대기 등록
- 예약 취소 시 대기 순서가 가장 빠른 회원 자동 승격
- 내 예약·대기 내역 조회
- 관리자 프로그램·회차 관리
- 인앱 알림 및 예약 상태 변경 이력

## 화면

데모 데이터(`db/demo/seed-demo.sh`)로 찍은 화면

| 회원 — 수업 찾기 | 관리자 — 대시보드 |
|---|---|
| ![회원 수업 찾기](docs/assets/screens/member-home.png) | ![관리자 대시보드](docs/assets/screens/admin-dashboard.png) |
| **회원 — 내 예약** | **회원 — 알림 (자동 승격 포함)** |
| ![내 예약](docs/assets/screens/member-my.png) | ![알림](docs/assets/screens/member-notifications.png) |
| **관리자 — 회차별 예약 (취소 → 대기 자동 승격)** | **관리자 — 주간 시간표** |
| ![회차별 예약](docs/assets/screens/admin-reservations.png) | ![주간 시간표](docs/assets/screens/admin-timetable.png) |

<details>
<summary>모바일 — 수업 찾기</summary>

<img src="docs/assets/screens/member-home-mobile.png" width="320" alt="모바일 수업 찾기">
</details>

## 문서

- [프로젝트 개요](docs/00_PROJECT_OVERVIEW.md)
- [요구사항 정의서 v0.1](docs/01_REQUIREMENTS_v0.1.md)
- [화면 설계서 v0.2 — 회원 목록형 + 관리자 주간 시간표](docs/02_SCREEN_DESIGN_v0.2.md)
- [화면 설계서 v0.1 (초안)](docs/02_SCREEN_DESIGN_v0.1.md)
- [UI 콘셉트 보드](docs/assets/slotmate_ui_reference_v0.2.png)
- [DB 설계서 v0.2 — 화면/API 명세 반영](docs/04_DB_DESIGN_v0.2.md)
- [DB 설계서 v0.1 (초안)](docs/04_DB_DESIGN_v0.1.md)
- [API 명세서 v0.2 — 단계 4 구현 반영](docs/05_API_SPEC_v0.2.md)
- [API 명세서 v0.1 (초안)](docs/05_API_SPEC_v0.1.md)
- [개발 단계 및 완료 기준](docs/03_ROADMAP.md)

## 기술 스택 초안

- Java 21
- Spring Boot (프로젝트 생성 시 호환되는 안정 버전 선택)
- Gradle (Groovy DSL)
- MariaDB
- Spring Web, Validation, Spring Security, Spring Data JPA
- OpenAPI/Swagger UI를 통한 API 문서화
- 테스트: JUnit 5, Spring Boot Test
- Git/GitHub, Docker는 핵심 기능 구현 이후 도입

세부 버전과 라이브러리는 프로젝트 초기화 시 공식 호환성을 확인해 확정합니다.

## 로컬 실행

필요: JDK 21, Docker Desktop

```bash
cp .env.example .env        # 값 채우기 (.env 는 git 제외)
docker compose up -d        # MariaDB 기동, 최초 1회 db/init/01_schema.sql 실행
./gradlew bootRun           # Windows: gradlew.bat bootRun
```

- 스키마를 다시 만들려면 `docker compose down -v` 로 볼륨까지 지운 뒤 다시 `up`
- 데모 데이터: 앱 실행 중 `./db/demo/seed-demo.sh` (zsh·jq 필요). 기존 회차·예약·알림은 지워짐

## 주의

이 프로젝트에는 실제 회사 코드, 회사 데이터, 비밀번호, API 키 또는 기타 비밀 정보를 넣지 않습니다.
