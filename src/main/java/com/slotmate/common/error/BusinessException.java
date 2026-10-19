package com.slotmate.common.error;

import java.util.List;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldError> fieldErrors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }

    /** 400 VALIDATION_ERROR — 필드 하나 */
    public static BusinessException invalidField(String field, String reason) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, List.of(new ErrorResponse.FieldError(field, reason)));
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public List<ErrorResponse.FieldError> getFieldErrors() { return fieldErrors; }
}
