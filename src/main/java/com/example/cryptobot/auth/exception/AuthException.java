package com.example.cryptobot.auth.exception;

import com.example.cryptobot.common.apiPayload.ErrorCode;

/**
 * 인증·토큰 검증 실패 예외.
 * JwtAuthenticationFilter 에서 던지고 AuthFailureHandler 가 처리합니다.
 */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
