package com.example.cryptobot.common.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 통합 응답 봉투.
 *
 * <p>성공:
 * <pre>{"isSuccess":true,"code":"COMMON200","message":"요청에 성공했습니다.","result":{...}}</pre>
 * 에러:
 * <pre>{"isSuccess":false,"code":"TIER403","message":"프리미엄이 필요한 기능이에요.","result":null}</pre>
 * </p>
 *
 * @param <T> 결과 데이터 타입
 */
@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean isSuccess;
    private final String code;
    private final String message;
    private final T result;

    /** 성공 응답 (기본 200) */
    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(), result);
    }

    /** 성공 응답 — 특정 ResultCode 지정 */
    public static <T> ApiResponse<T> onSuccess(ResultCode code, T result) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    /** 에러 응답 */
    public static <T> ApiResponse<T> onFailure(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 에러 응답 — 메시지 오버라이드 (필드 에러 상세 등) */
    public static <T> ApiResponse<T> onFailure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }
}
