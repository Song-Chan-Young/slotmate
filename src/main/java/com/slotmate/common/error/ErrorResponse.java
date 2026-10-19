package com.slotmate.common.error;

import java.util.List;

/** API 명세 4절 공통 오류 응답 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
