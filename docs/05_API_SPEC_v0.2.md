# SlotMate API 명세서 v0.2

- 작성일: 2026-10-19 (v0.1) · 2026-10-20 (v0.2)
- 상태: 초안 — 단계 4 구현(인증·관리자 프로그램/회차·회원 조회·예약 생성)에서 정한 것 반영. 변경 내역은 13절
- Base URL: `/api/v1`
- Content-Type: `application/json`
- 시간대: 사용자가 입력하는 날짜/시간은 `Asia/Seoul` 기준

## 1. API 설계 원칙

1. API는 화면 구현과 분리된 HTTP/JSON 계약으로 정의한다.
2. 회원용 조회는 공개 범위와 본인 예약 정보를 구분한다. 로그인하지 않은 경우 `myReservationStatus`, `myWaitingPosition`은 `null`로 반환한다.
3. 예약 가능 여부는 화면에서 판단한 값이 아니라 요청을 처리하는 서버와 DB가 최종 결정한다.
4. 오류 응답의 `code`는 프런트엔드가 메시지와 동작을 일관되게 처리할 수 있도록 고정된 값으로 사용한다.
5. 예약 생성·취소·대기 승격은 트랜잭션으로 처리하고, 정원 및 중복 예약 규칙은 서버와 DB에서 함께 보호한다.
6. 회원은 본인 데이터만 조회·취소할 수 있다. `/admin/**` API는 `ADMIN` 권한이 필요하다.

## 2. 인증 방식

- 회원가입과 로그인은 공개 API다.
- 로그인 성공 시 `accessToken`과 간단한 사용자 정보를 반환한다.
- 로그인이 필요한 요청은 `Authorization: Bearer {accessToken}` 헤더를 사용한다.
- 비밀번호는 응답에 포함하지 않으며, DB에는 해시만 저장한다.

| 항목 | v0.2 확정 |
|---|---|
| 토큰 | JWT HS256 (Spring Security OAuth2 Resource Server). 서명 키는 `.env` 의 `JWT_SECRET` |
| 클레임 | `sub` = userId, `role` = `MEMBER`/`ADMIN` |
| 만료 | 3600초. 갱신·로그아웃은 미정(12절) |
| 세션 | 서버 세션 없음 (stateless) |
| 비밀번호 해시 | `{bcrypt}` (DelegatingPasswordEncoder) |
| 관리자 계정 | 앱 기동 시 `.env` 의 `ADMIN_EMAIL`·`ADMIN_PASSWORD` 계정이 없으면 `ADMIN` 으로 생성. 회원가입은 항상 `MEMBER` |
| 공개 경로 | `/auth/**`, `GET /programs/**`, `GET /sessions/**`, Swagger UI·`/v3/api-docs` |
| 공개 경로에 토큰 | 유효하면 본인 예약 상태 포함. 잘못된 토큰이면 공개 경로라도 401 |

## 3. 공통 성공 응답

단건 응답은 리소스 객체를 반환한다. 목록은 `items`와 `page` 정보를 함께 반환한다.

목록 예시:

```json
{
  "items": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

페이지 번호는 0부터 시작한다. 별도 표기가 없는 날짜 필터는 `YYYY-MM-DD` 형식이다.

## 4. 공통 오류 응답

```json
{
  "code": "DUPLICATE_ACTIVE_RESERVATION",
  "message": "이미 이 수업에 예약 또는 대기 등록되어 있습니다.",
  "fieldErrors": []
}
```

| HTTP | 사용 상황 | 예시 코드 |
|---:|---|---|
| 400 | 형식 오류, 필수값 누락 | `VALIDATION_ERROR` |
| 401 | 로그인 필요 또는 토큰 오류 | `UNAUTHENTICATED` |
| 403 | 권한 없음 | `FORBIDDEN` |
| 404 | 대상 리소스 없음 | `RESOURCE_NOT_FOUND` |
| 409 | 중복 예약, 상태 충돌, 정원 변경 불가 | `DUPLICATE_ACTIVE_RESERVATION`, `SESSION_NOT_OPEN`, `CAPACITY_BELOW_CONFIRMED_COUNT` |
| 500 | 예상하지 못한 서버 오류 | `INTERNAL_SERVER_ERROR` |

v0.2 구현 코드 (`ErrorCode`):

| HTTP | 코드 | 상황 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 본문 검증 실패, JSON 형식 오류, 필수 쿼리 누락, 쿼리 형식 오류 |
| 401 | `UNAUTHENTICATED` | 토큰 없음·잘못됨·만료 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 (계정 없음·비밀번호 틀림 구분 안 함) |
| 403 | `FORBIDDEN` | 역할 부족 (`/admin/**` 에 MEMBER, 예약 신청에 ADMIN) |
| 404 | `RESOURCE_NOT_FOUND` | 대상 없음. 회원용 API 에서 `DRAFT` 회차·비활성 프로그램 포함 |
| 409 | `EMAIL_ALREADY_EXISTS` | 가입 이메일 중복 |
| 409 | `DUPLICATE_ACTIVE_RESERVATION` | 같은 회차에 확정·대기 예약 있음 |
| 409 | `SESSION_NOT_OPEN` | `OPEN` 아님 또는 이미 시작한 회차 |
| 409 | `CAPACITY_BELOW_CONFIRMED_COUNT` | 확정 인원보다 작은 정원으로 변경 |
| 409 | `INVALID_STATUS_TRANSITION` | 9.4 전이표에 없는 상태 변경 |
| 409 | `SESSION_HAS_ACTIVE_RESERVATIONS` | 확정·대기 예약 있는 회차의 시간 변경·취소 |
| 409 | `INSTRUCTOR_NOT_ACTIVE` | 비활성 강사 배정 |
| 409 | `RESERVATION_NOT_CANCELLABLE` | 이미 취소한 예약, 시작한 회차의 예약 취소 |

- `500 INTERNAL_SERVER_ERROR` 공통 처리는 아직 없음 (로드맵 단계 6)

검증 오류의 `fieldErrors` 예시:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [
    { "field": "endAt", "reason": "종료 시각은 시작 시각보다 늦어야 합니다." }
  ]
}
```

## 5. 인증 API

### 5.1 회원가입

- `POST /auth/signup`
- 인증: 불필요

요청:

```json
{
  "displayName": "김지민",
  "email": "jimin@example.com",
  "password": "ExamplePassword123!"
}
```

응답: `201 Created`

```json
{
  "userId": 101,
  "displayName": "김지민",
  "email": "jimin@example.com",
  "role": "MEMBER"
}
```

주요 오류: `400 VALIDATION_ERROR`, `409 EMAIL_ALREADY_EXISTS`.

### 5.2 로그인

- `POST /auth/login`
- 인증: 불필요

요청:

```json
{
  "email": "jimin@example.com",
  "password": "ExamplePassword123!"
}
```

응답: `200 OK`

```json
{
  "accessToken": "{issued-token}",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "userId": 101,
    "displayName": "김지민",
    "role": "MEMBER"
  }
}
```

인증 실패 시 계정 존재 여부를 노출하지 않고 `401 INVALID_CREDENTIALS`를 반환한다.

## 6. 회원용 조회 API

### 6.1 프로그램 목록

- `GET /programs`
- 인증: 불필요
- Query: `type`(선택, 예: `PT`, `YOGA`, `GX`), `keyword`(선택), `page`(기본 0), `size`(기본 20)
- 기본적으로 활성 프로그램만 반환한다.

응답 항목 예시:

```json
{
  "items": [
    {
      "programId": 10,
      "name": "기초 요가",
      "type": "YOGA",
      "description": "초보자를 위한 기초 요가 수업",
      "defaultDurationMinutes": 50,
      "defaultCapacity": 10
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

### 6.2 프로그램 상세 및 예정 회차

- `GET /programs/{programId}`
- 인증: 불필요
- Query: `from`, `to`(선택, 날짜 기준)
- 프로그램 설명과 공개된 예정 회차를 반환한다.

### 6.3 날짜별 수업 목록

- `GET /sessions`
- 인증: 불필요. 로그인한 경우 본인 예약 상태를 추가 반환한다.
- Query:
  - `date` 필수: `YYYY-MM-DD`
  - `type` 선택: `PT`, `YOGA`, `GX` 등
  - `keyword` 선택: 프로그램명 또는 강사명 검색
  - `page`, `size` 선택
- 기본 정렬: 시작 시각 오름차순.

응답 항목 예시:

```json
{
  "items": [
    {
      "sessionId": 501,
      "programId": 10,
      "programName": "기초 요가",
      "programType": "YOGA",
      "instructorName": "이 강사",
      "startAt": "2026-10-20T13:00:00+09:00",
      "endAt": "2026-10-20T13:50:00+09:00",
      "capacity": 10,
      "confirmedCount": 10,
      "waitingCount": 2,
      "availableSeats": 0,
      "sessionStatus": "OPEN",
      "bookingAction": "WAITLISTABLE",
      "myReservationStatus": null,
      "myWaitingPosition": null
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

`bookingAction` 후보: `BOOKABLE`, `WAITLISTABLE`, `ALREADY_CONFIRMED`, `ALREADY_WAITING`, `CLOSED`, `CANCELLED`, `COMPLETED`, `STARTED`.

### 6.4 수업 상세

- `GET /sessions/{sessionId}`
- 인증: 불필요. 로그인한 경우 본인 예약 상태 포함
- 응답은 수업 소개, 강사, 시작/종료 시각, 장소(설정된 경우), 정원 및 예약 요약을 반환한다.

## 7. 회원 예약 API

### 7.1 예약 또는 대기 신청

- `POST /sessions/{sessionId}/reservations`
- 인증: MEMBER
- 요청 본문: 없음
- 응답: `201 Created`

자리가 남아 있으면 `CONFIRMED`, 만석이지만 대기를 허용하면 `WAITING`을 생성한다. 대기 등록 시 서버가 계산한 현재 대기 순번을 반환한다.

확정 예시:

```json
{
  "reservationId": 9001,
  "sessionId": 501,
  "reservationStatus": "CONFIRMED",
  "waitingPosition": null,
  "message": "예약이 확정되었습니다."
}
```

대기 예시:

```json
{
  "reservationId": 9002,
  "sessionId": 502,
  "reservationStatus": "WAITING",
  "waitingPosition": 3,
  "message": "대기 신청이 완료되었습니다."
}
```

주요 오류:
- `409 DUPLICATE_ACTIVE_RESERVATION`: 동일 회차에 확정 또는 대기 예약이 이미 존재
- `409 SESSION_NOT_OPEN`: 예약을 받지 않는 회차
- `404 RESOURCE_NOT_FOUND`: 존재하지 않는 회차

마지막 좌석을 두고 동시 요청이 들어오더라도 서버가 회차 단위로 동시성을 제어해 정원을 초과하지 않게 한다. 요청 시점에 이미 자리가 찼고 대기가 허용되면 대기로 등록한다.

### 7.2 내 예약 목록

- `GET /my/reservations`
- 인증: MEMBER
- Query: `status` 선택(`CONFIRMED`, `WAITING`, `CANCELLED`), `from`, `to`, `page`, `size`
- 본인 데이터만 반환한다. 대기 상태에는 조회 시점의 대기 순번을 포함한다.

### 7.3 예약 취소 또는 대기 취소

- `DELETE /reservations/{reservationId}`
- 인증: MEMBER
- 응답: `200 OK`

```json
{
  "reservationId": 9001,
  "reservationStatus": "CANCELLED",
  "message": "예약이 취소되었습니다."
}
```

규칙:
- 본인 예약만 취소할 수 있다.
- 회차 시작 전까지만 직접 취소할 수 있다(초안 정책).
- 대기 취소는 해당 대기 항목만 취소한다.
- 확정 예약 취소 시 회차가 `OPEN`이고 대기자가 있으면 가장 오래 기다린 대기자 한 명을 같은 트랜잭션에서 확정한다.
- 관련 상태 이력과 인앱 알림도 트랜잭션 규칙에 맞게 저장한다.

주요 오류: `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`, `409 RESERVATION_NOT_CANCELLABLE`.

## 8. 알림 API

### 8.1 내 알림 목록

- `GET /my/notifications`
- 인증: MEMBER
- Query: `unreadOnly`(기본 false), `page`, `size`

### 8.2 알림 읽음 처리

- `PATCH /my/notifications/{notificationId}/read`
- 인증: MEMBER
- 본인의 알림만 읽음 처리한다. 다른 사용자의 알림 ID로 접근하면 데이터 존재 여부를 과도하게 노출하지 않는 정책을 사용한다.

## 9. 관리자 API

모든 `/admin/**` 경로는 `ADMIN` 권한을 요구한다.

### 9.1 프로그램 관리

| 메서드 | URL | 설명 |
|---|---|---|
| GET | `/admin/programs` | 전체 프로그램 조회 및 검색 |
| POST | `/admin/programs` | 프로그램 등록 |
| PATCH | `/admin/programs/{programId}` | 이름·설명·기본 시간·기본 정원 등 수정 |
| PATCH | `/admin/programs/{programId}/active` | 활성/비활성 변경 |

등록 요청 예시:

```json
{
  "name": "기초 요가",
  "type": "YOGA",
  "description": "초보자를 위한 기초 요가 수업",
  "defaultDurationMinutes": 50,
  "defaultCapacity": 10
}
```

### 9.2 관리자 시간표 조회

- `GET /admin/sessions`
- Query: `from`과 `to` 날짜 필수, `type`, `status` 선택
- 화면의 주간 시간표가 필요로 하는 기간의 회차를 반환한다.
- 응답에는 프로그램, 강사, 시간, 정원, 확정 인원, 대기 인원, 회차 상태를 포함한다.

### 9.3 회차 생성

- `POST /admin/sessions`
- 요청 예시:

```json
{
  "programId": 10,
  "instructorId": 3,
  "startAt": "2026-10-20T13:00:00+09:00",
  "endAt": "2026-10-20T13:50:00+09:00",
  "capacity": 10,
  "location": "2층 GX룸",
  "description": "매트 준비물을 지참해 주세요.",
  "initialStatus": "DRAFT"
}
```

- 종료 시각이 시작 시각보다 늦어야 하며, 정원은 1 이상이어야 한다.
- 강사가 지정되면 활성 상태의 강사인지 검증한다.
- 같은 강사의 다른 수업과 시간이 겹치는지 검사하는 기능은 정책 확정 후 추가 가능하다.

### 9.4 회차 수정 및 상태 변경

| 메서드 | URL | 설명 |
|---|---|---|
| PATCH | `/admin/sessions/{sessionId}` | 허용된 회차 정보 수정 |
| PATCH | `/admin/sessions/{sessionId}/status` | `DRAFT`, `OPEN`, `CLOSED`, `CANCELLED` 상태 변경 |

수정 요청 예시:

```json
{
  "instructorId": 3,
  "startAt": "2026-10-20T13:00:00+09:00",
  "endAt": "2026-10-20T13:50:00+09:00",
  "capacity": 10,
  "location": "2층 GX룸"
}
```

- 확정 예약 인원보다 작은 정원으로 변경할 수 없다.
- 이미 예약자가 있는 회차의 시간 변경·취소는 서버에서 상태 및 영향을 검증하고, 불가능하면 `409`를 반환한다.
- 상태 변경 API는 허용된 상태 전이만 처리한다.

### 9.5 회차별 예약자·대기자 조회

- `GET /admin/sessions/{sessionId}/reservations`
- Query: `status` 선택, `page`, `size`
- 확정/대기/취소 항목을 필터링하며, 대기 항목은 `requestedAt`, `reservationId` 오름차순으로 반환한다.
- 관리자는 운영에 필요한 회원 최소 정보만 조회한다. 비밀번호 및 토큰은 반환하지 않는다.

응답 항목 예시:

```json
{
  "items": [
    {
      "reservationId": 9002,
      "memberDisplayName": "김지민",
      "memberEmail": "jimin@example.com",
      "reservationStatus": "WAITING",
      "waitingPosition": 1,
      "requestedAt": "2026-10-19T11:15:00+09:00"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

## 10. 핵심 상태 및 동시성 규칙

1. 예약 신청, 취소, 대기 승격은 서비스 계층에서 트랜잭션으로 처리한다.
2. 예약 신청 시 해당 `class_sessions` 행을 잠그고 최신 확정 수와 활성 예약 여부를 확인한다.
3. 정원이 남으면 `CONFIRMED`, 정원이 없으면 `WAITING`으로 생성한다.
4. 같은 회원/회차의 활성 예약은 하나만 허용한다. DB 제약 오류도 서비스에서 적절한 `409`로 변환한다.
5. 확정 예약 취소와 대기자 승격은 한 트랜잭션으로 묶는다. 중간 단계 실패 시 일부 상태만 변경되어서는 안 된다.
6. 대기 순번은 `requestedAt`, `reservationId` 오름차순으로 계산한다. 화면에 표시하는 순번은 조회 시점 기준이다.
7. 관리자 정원 변경은 최신 확정 예약 수를 확인한 후 저장한다.

## 11. 화면-API 연결표

| 화면/동작 | API |
|---|---|
| 회원 로그인 | `POST /auth/login` |
| 회원가입 | `POST /auth/signup` |
| 날짜별 수업 목록 | `GET /sessions?date=...&type=...` |
| 수업 상세 | `GET /sessions/{sessionId}` |
| 예약 또는 대기 신청 | `POST /sessions/{sessionId}/reservations` |
| 내 예약/대기 내역 | `GET /my/reservations` |
| 예약/대기 취소 | `DELETE /reservations/{reservationId}` |
| 알림 목록 및 읽음 처리 | `GET /my/notifications`, `PATCH /my/notifications/{notificationId}/read` |
| 관리자 프로그램 관리 | `/admin/programs` |
| 관리자 주간 시간표 | `GET /admin/sessions?from=...&to=...` |
| 관리자 회차 등록/수정/상태 변경 | `/admin/sessions` 및 `/admin/sessions/{sessionId}` |
| 관리자 예약·대기자 목록 | `GET /admin/sessions/{sessionId}/reservations` |

## 12. 아직 확정하지 않은 사항

- 토큰 갱신 및 로그아웃 정책
- 회차 취소 시 기존 확정·대기 예약의 상태 처리 — v0.2 는 예약이 있으면 취소 자체를 409 로 막음(임시)
- 강사 시간 중복 검증을 1차 버전에 포함할지 여부
- 회원 관리 API를 1차 또는 후속 버전에 넣을지 여부 — v0.2 는 조회만 추가 (13.8)
- `location`과 `description` 필드의 최종 길이 및 필수 여부 — v0.2 는 DB 컬럼 길이(location 150)만 검사
- 강사 관리 API — 명세에 없음. v0.2 는 목록 조회만 추가(13.8), 등록·수정은 DB 직접 입력
- 탈퇴(`WITHDRAWN`) 계정의 로그인 처리

이 명세는 화면과 백엔드 간의 초안 계약이다. 실제 구현과 통합 테스트를 진행하면서 변경 사항은 버전으로 기록한다.

## 13. v0.1 → v0.2 변경 내역 (구현에서 정한 것)

### 13.1 인증 (5절)

| 항목 | 내용 |
|---|---|
| 회원가입 검증 | `displayName` 필수·80자 이하, `email` 필수·형식·255자 이하, `password` 8~64자 |
| 로그인 검증 | `email`, `password` 필수 |

### 13.2 회원용 조회 (6절)

| 항목 | 내용 |
|---|---|
| 공개 범위 | `DRAFT` 회차는 목록에서 빠지고 상세는 404. `CLOSED`·`CANCELLED`·`COMPLETED` 는 보임 |
| 6.1 프로그램 목록 | `keyword` 는 프로그램명 부분 일치. 정렬 `programId` 오름차순 |
| 6.2 프로그램 상세 | 비활성 프로그램 404. `sessions` 배열에 지금 이후 시작하는 공개 회차(6.3 항목 형식), 시작 시각 순, 페이징 없음. `from`·`to` 날짜는 둘 다 포함 |
| 6.3 날짜별 목록 | `availableSeats` = max(0, 정원 − 확정). `myWaitingPosition` 은 조회 시점 계산 |
| 6.4 수업 상세 | 6.3 항목 + `programDescription`(수업 소개), `description`(회차 설명), `location` |

`bookingAction` 판단 순서 (먼저 맞는 것):

| 순서 | 조건 | 값 |
|---:|---|---|
| 1 | 회차 `CANCELLED` | `CANCELLED` |
| 2 | 회차 `COMPLETED` | `COMPLETED` |
| 3 | 내 예약 `CONFIRMED` | `ALREADY_CONFIRMED` |
| 4 | 내 예약 `WAITING` | `ALREADY_WAITING` |
| 5 | 시작 시각 ≤ 지금 | `STARTED` |
| 6 | 회차 `OPEN` 아님 (`CLOSED`) | `CLOSED` |
| 7 | 확정 < 정원 | `BOOKABLE` |
| 8 | 그 외 | `WAITLISTABLE` |

### 13.3 예약 신청 (7.1)

| 항목 | 내용 |
|---|---|
| 권한 | `MEMBER` 만. `ADMIN` 은 403 |
| `DRAFT` 회차 | 404 `RESOURCE_NOT_FOUND` |
| 잠금 | 회차 행 `SELECT … FOR UPDATE` 후 확정 수 확인 (DB 설계서 6.1) |
| UNIQUE 위반 | 409 `DUPLICATE_ACTIVE_RESERVATION` 으로 변환 (10절 4) |
| 재신청 | 취소된 예약이 있으면 같은 행 재사용, `attemptNo` +1 |
| 이력 | 같은 트랜잭션에 저장. 첫 신청 `BOOKED`/`WAITLISTED`, 재신청 `REBOOKED` |
| 알림 | 확정 `BOOKING_CONFIRMED`, 대기 `WAITLISTED` (13.7) |
| 동시 요청 검증 | 테스트는 단계 5 |

### 13.4 관리자 프로그램 (9.1)

| 항목 | 내용 |
|---|---|
| 목록 쿼리 | `type`, `keyword`(프로그램명), `page`, `size`. 비활성 포함 |
| 응답 | 6.1 항목 + `active` |
| 등록 검증 | `name` 필수·100자 이하, `type` 필수, `defaultDurationMinutes` 1~32767, `defaultCapacity` 1 이상 |
| 수정 | 보낸 항목만 수정 (이름·설명·기본 시간·기본 정원). `type` 은 수정 안 됨. 값을 비우는 방법은 없음 |
| 활성 변경 | 본문 `{"active": true}` 또는 `{"active": false}` |
| 비활성 프로그램 | 관리자 회차 생성은 막지 않음 |

### 13.5 관리자 회차 (9.2~9.4)

| 항목 | 내용 |
|---|---|
| 시간 입력 | 오프셋 포함 ISO-8601. `Z` 등 다른 오프셋도 서울 시간으로 바꿔 저장. 응답은 `+09:00` |
| 9.2 조회 | `from`·`to` 둘 다 포함, `from` > `to` 면 400. `page`·`size`(기본 20). 시작 시각 순 |
| 응답 항목 | `sessionId`, `programId`, `programName`, `programType`, `instructorId`, `instructorName`, `startAt`, `endAt`, `capacity`, `confirmedCount`, `waitingCount`, `location`, `description`, `sessionStatus` |
| 9.3 `initialStatus` | `DRAFT`·`OPEN` 만, 생략 시 `DRAFT`. 그 외 400 |
| 9.3 강사 | 없는 강사 404, 비활성 강사 409 `INSTRUCTOR_NOT_ACTIVE` |
| 9.4 수정 | 보낸 항목만 수정 (`instructorId`, `startAt`, `endAt`, `capacity`, `location`). 수정 후에도 종료 > 시작 |
| 9.4 예약 있는 회차 | 확정·대기 예약이 있으면 시간 변경과 `CANCELLED` 전이 409 `SESSION_HAS_ACTIVE_RESERVATIONS` |
| 9.4 정원 | 확정 인원보다 작으면 409 `CAPACITY_BELOW_CONFIRMED_COUNT` |
| 잠금 | 수정·상태 변경 시 회차 행 잠금 |
| `CANCELLED`·`COMPLETED` 회차 | 정보 수정은 막지 않음 |

상태 전이표 (9.4):

| 현재 | 바꿀 수 있는 상태 |
|---|---|
| `DRAFT` | `OPEN`, `CANCELLED` |
| `OPEN` | `CLOSED`, `CANCELLED` |
| `CLOSED` | `OPEN`, `CANCELLED` |
| `CANCELLED` | 없음 |
| `COMPLETED` | 없음 (API 로 만들지 않음) |

### 13.6 내 예약 · 취소 · 회차별 예약자 (7.2 · 7.3 · 9.5)

| 항목 | 내용 |
|---|---|
| 7.2 권한 | `MEMBER` 만. `ADMIN` 은 403 |
| 7.2 쿼리 | `status`, `from`·`to`(회차 시작 날짜, 둘 다 포함, `from` > `to` 면 400), `page`, `size`(기본 20) |
| 7.2 정렬 | 회차 시작 시각 내림차순 |
| 7.2 응답 항목 | `reservationId`, `sessionId`, `programName`, `programType`, `instructorName`, `startAt`, `endAt`, `location`, `sessionStatus`, `reservationStatus`, `waitingPosition`(WAITING 만), `requestedAt`, `confirmedAt`, `cancelledAt` |
| 7.3 권한 | `MEMBER` 만. 남의 예약 403, 없는 예약 404 |
| 7.3 잠금 | 회차 행 `SELECT … FOR UPDATE` 후 예약 조회 (신청과 같은 순서). 잠금 뒤 읽는 예약·확정 목록·첫 대기자도 잠금 조회 — 잠금 전 일반 조회로 고정된 REPEATABLE READ 스냅샷을 피함 (`loadtest/RESULTS.md`) |
| 7.3 취소 불가 | `CANCELLED` 예약, 시작 시각이 지난 회차 → 409 `RESERVATION_NOT_CANCELLABLE` |
| 7.3 메시지 | 확정 취소 「예약이 취소되었습니다.」, 대기 취소 「대기가 취소되었습니다.」 |
| 7.3 승격 | 확정 취소 + 회차 `OPEN` + 확정 수 < 정원이면 가장 오래 기다린 대기 1명을 같은 트랜잭션에서 `CONFIRMED`. `CLOSED` 회차는 승격 안 함 |
| 7.3 이력 | 취소 `CANCELLED`(actor 본인), 승격 `PROMOTED`(actor 없음) |
| 7.3 알림 | 취소 본인 `CANCELLED`, 승격된 회원 `PROMOTED` (13.7) |
| 9.5 쿼리 | `status`, `page`, `size`(기본 20). 없는 회차 404 |
| 9.5 정렬 | `requestedAt`, `reservationId` 오름차순 |
| 9.5 응답 항목 | 명세 예시 항목 + `confirmedAt`, `cancelledAt`. `confirmedAt` > `requestedAt` 이면 대기에서 승격된 것 |
| 동시 요청 검증 | 테스트는 단계 5 |

### 13.7 알림 (8절)

| 항목 | 내용 |
|---|---|
| 생성 시점 | 예약 확정·대기 신청·취소·자동 승격. 상태 변경과 같은 트랜잭션 |
| 제목 | 「예약이 확정되었습니다」 「대기 신청이 완료되었습니다」 「예약이 취소되었습니다」·「대기가 취소되었습니다」 「대기하던 수업이 예약 확정되었습니다」 |
| 본문 | `10월 22일 (목) 07:00 매트 필라테스` 형식. 대기는 「· 현재 대기 N번째」, 승격은 자동 확정 안내를 덧붙임 |
| 8.1 권한·정렬 | `MEMBER` 만. 생성 최신순. `unreadOnly`, `page`, `size`(기본 20) |
| 8.1 응답 항목 | `notificationId`, `notificationType`, `title`, `message`, `read`, `createdAt`, `readAt`, `reservationId`, `sessionId` |
| 8.2 | 남의 알림·없는 알림 모두 404 `RESOURCE_NOT_FOUND`. 이미 읽은 알림은 그대로 200 |
| 읽지 않은 개수 | 별도 API 없음. 화면은 `unreadOnly=true&size=1` 의 `totalElements` 사용 |

### 13.8 관리자 조회 추가 (명세에 없던 것)

| API | 내용 |
|---|---|
| `GET /admin/instructors` | 수업 폼 강사 선택용. 비활성 포함, 이름순, 페이지 없음. 항목 `instructorId`, `name`, `specialty`, `active`. 등록·수정 API 는 아직 없음 |
| `GET /admin/members` | 화면 A05. `MEMBER` 만. `keyword`(이름·이메일), `status`(`ACTIVE`·`WITHDRAWN`), `page`, `size`. 가입 최신순. 항목 `userId`, `displayName`, `email`, `accountStatus`, `createdAt`. 조회만 |

### 13.9 동시 요청 검증 (단계 5)

| 테스트 | 확인한 것 |
|---|---|
| 마지막 한 자리 20명 동시 신청 | 확정 1, 대기 19 |
| 같은 회원 20번 동시 신청 | 활성 예약 1건, 나머지 409 |
| 확정 취소와 신규 신청 동시 | 확정 1건, 가장 오래 기다린 대기자가 승격 |

- 회차 행 잠금(`findByIdForUpdate`)을 빼면 첫 테스트가 실패하는 것 확인
