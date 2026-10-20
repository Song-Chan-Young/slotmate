package com.slotmate.common.error;

import org.springframework.http.HttpStatus;

/** API 명세 4절 오류 코드 — 지금 쓰는 것만 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    CAPACITY_BELOW_CONFIRMED_COUNT(HttpStatus.CONFLICT, "정원은 확정 예약 인원보다 작을 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 회차 상태 변경입니다."),
    SESSION_HAS_ACTIVE_RESERVATIONS(HttpStatus.CONFLICT, "확정·대기 예약이 있는 회차는 시간 변경·취소할 수 없습니다."),
    INSTRUCTOR_NOT_ACTIVE(HttpStatus.CONFLICT, "배정할 수 없는 강사입니다."),
    DUPLICATE_ACTIVE_RESERVATION(HttpStatus.CONFLICT, "이미 이 수업에 예약 또는 대기 등록되어 있습니다."),
    SESSION_NOT_OPEN(HttpStatus.CONFLICT, "예약을 받지 않는 수업입니다."),
    RESERVATION_NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 없는 예약입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
