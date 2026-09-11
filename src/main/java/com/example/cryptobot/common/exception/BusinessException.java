package com.example.cryptobot.common.exception;

import com.example.cryptobot.common.apiPayload.ErrorCode;

/**
 * 도메인 비즈니스 규칙 위반 예외.
 * GlobalExceptionHandler 에서 ErrorCode 에 해당하는 HTTP 상태와 봉투로 응답합니다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 메시지 오버라이드가 필요한 경우 (예: 필드명 포함) */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
