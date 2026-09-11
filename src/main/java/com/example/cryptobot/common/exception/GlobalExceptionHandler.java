package com.example.cryptobot.common.exception;

import com.example.cryptobot.common.apiPayload.ApiResponse;
import com.example.cryptobot.common.apiPayload.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 전역 예외 핸들러.
 *
 * <p>모든 응답은 {@link ApiResponse} 봉투로 통일됩니다.
 * 인증·인가 에러(401/403)는 필터 단계에서 처리되므로
 * {@link com.example.cryptobot.auth.exception.AuthFailureHandler} 가 담당합니다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 도메인 비즈니스 규칙 위반 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        // 메시지가 ErrorCode 기본값과 다르면(오버라이드된 경우) 커스텀 메시지 사용
        String message = e.getMessage() != null ? e.getMessage() : errorCode.getMessage();
        log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), message);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode, message));
    }

    /** @Valid 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.VALIDATION_FAILED, message));
    }

    /** 잘못된 인자 (도메인 검증 실패 — 직접 메시지 포함) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage();
        log.warn("[IllegalArgumentException] {}", msg);
        return ResponseEntity
                .status(ErrorCode._BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode._BAD_REQUEST, msg));
    }

    /** 리소스 미존재 */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(jakarta.persistence.EntityNotFoundException e) {
        return ResponseEntity
                .status(ErrorCode._NOT_FOUND.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode._NOT_FOUND, e.getMessage()));
    }

    /** 그 외 미처리 예외 — 스택트레이스는 로그에만 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        return ResponseEntity
                .status(ErrorCode._INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode._INTERNAL_ERROR));
    }
}
