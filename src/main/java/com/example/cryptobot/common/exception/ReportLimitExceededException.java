package com.example.cryptobot.common.exception;

/**
 * FREE 티어 월 리포트 생성 한도 초과 예외.
 * GlobalExceptionHandler 에서 HTTP 429 로 처리됩니다.
 */
public class ReportLimitExceededException extends RuntimeException {

    public ReportLimitExceededException(String message) {
        super(message);
    }
}
