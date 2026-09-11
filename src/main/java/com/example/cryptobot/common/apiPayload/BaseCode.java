package com.example.cryptobot.common.apiPayload;

import org.springframework.http.HttpStatus;

/**
 * 성공·에러 코드 공통 인터페이스.
 * ResultCode(성공) 와 ErrorCode(에러) 가 모두 구현합니다.
 */
public interface BaseCode {

    /** 응답 코드 문자열 (예: "COMMON200", "AUTH401") */
    String getCode();

    /** 응답 메시지 */
    String getMessage();

    /** HTTP 상태 코드 */
    HttpStatus getHttpStatus();
}
