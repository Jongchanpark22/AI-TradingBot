package com.example.cryptobot.common.apiPayload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 성공 응답 코드 열거형.
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements BaseCode {

    /** 공통 성공 */
    SUCCESS("COMMON200", "요청에 성공했습니다.", HttpStatus.OK),
    CREATED("COMMON201", "생성에 성공했습니다.", HttpStatus.CREATED),
    NO_CONTENT("COMMON204", "처리에 성공했습니다.", HttpStatus.NO_CONTENT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
