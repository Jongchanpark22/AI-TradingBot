package com.example.cryptobot.common.apiPayload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 응답 코드 열거형.
 *
 * <p>프론트는 {@code code} 값으로 분기합니다.
 * TIER403 계열은 업셀 UI 트리거용입니다.</p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode implements BaseCode {

    // ─── 공통 ────────────────────────────────────────────────────────────────
    _INTERNAL_ERROR("COMMON500", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    _BAD_REQUEST("COMMON400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    _UNAUTHORIZED("COMMON401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    _FORBIDDEN("COMMON403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    _NOT_FOUND("COMMON404", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ─── 인증 ────────────────────────────────────────────────────────────────
    EMAIL_DUPLICATE("AUTH409", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_TOKEN("AUTH401", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("AUTH401", "만료된 토큰입니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("AUTH401", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    WITHDRAWN_USER("AUTH401", "탈퇴한 회원입니다.", HttpStatus.UNAUTHORIZED),

    // ─── 티어 ────────────────────────────────────────────────────────────────
    REPORT_LIMIT_EXCEEDED("TIER403", "이번 달 AI 리포트 생성 한도(5회)를 초과했습니다. 프리미엄으로 업그레이드하세요.",
            HttpStatus.FORBIDDEN),
    PREMIUM_REQUIRED("TIER403", "프리미엄 회원만 사용할 수 있는 기능입니다. 업그레이드하세요.",
            HttpStatus.FORBIDDEN),

    // ─── 도메인 ──────────────────────────────────────────────────────────────
    VALIDATION_FAILED("COMMON400", "입력값 검증에 실패했습니다.", HttpStatus.BAD_REQUEST),
    POSITION_NOT_FOUND("COMMON404", "포지션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND("COMMON404", "기본 계정이 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
