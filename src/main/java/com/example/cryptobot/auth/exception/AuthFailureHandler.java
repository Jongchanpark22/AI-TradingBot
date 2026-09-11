package com.example.cryptobot.auth.exception;

import com.example.cryptobot.common.apiPayload.ApiResponse;
import com.example.cryptobot.common.apiPayload.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증(401)·인가(403) 실패를 GlobalExceptionHandler 와 동일한 ApiResponse 봉투로 응답합니다.
 *
 * <p>Spring Security 필터 단계에서 발생하는 에러는 @RestControllerAdvice 가 잡지 못하므로
 * 이 핸들러가 직접 JSON 을 작성합니다.</p>
 *
 * <ul>
 *   <li>AuthenticationEntryPoint — 미인증 요청(401)</li>
 *   <li>AccessDeniedHandler — 권한 부족(403)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFailureHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 미인증 요청 → 401 */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[AuthFailure] 미인증 요청: uri={}, reason={}", request.getRequestURI(), authException.getMessage());
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode._UNAUTHORIZED);
    }

    /** 권한 부족 → 403 */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[AuthFailure] 권한 부족: uri={}, reason={}", request.getRequestURI(), accessDeniedException.getMessage());
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode._FORBIDDEN);
    }

    private void writeJson(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.onFailure(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
