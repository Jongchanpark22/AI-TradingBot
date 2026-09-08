package com.example.cryptobot.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 액세스 토큰 발급·검증 유틸리티.
 *
 * <p>액세스 토큰: 1시간, 클레임에 userId 포함.
 * 리프레시 토큰: UUID 원문(클라이언트 보관), SHA-256 해시를 DB 저장.</p>
 */
@Slf4j
@Component
public class JwtProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms:3600000}")
    private long accessTokenExpiryMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("[JWT] 서명 키 초기화 완료 (알고리즘: HS256)");
    }

    /**
     * 액세스 토큰을 발급합니다.
     *
     * @param userId 서비스 회원 ID
     * @param role   회원 권한 (예: "USER", "ADMIN")
     * @return 서명된 JWT 문자열
     */
    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 리프레시 토큰 원문(UUID)을 생성합니다.
     * DB 에는 이 값의 SHA-256 해시만 저장합니다.
     *
     * @return 원문 UUID 문자열
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    /**
     * 액세스 토큰에서 userId 를 추출합니다.
     *
     * @param token Bearer 토큰 (Bearer 접두사 제외)
     * @return userId
     * @throws JwtException 토큰이 유효하지 않은 경우
     */
    public Long getUserId(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    /**
     * 액세스 토큰에서 role 을 추출합니다. 클레임이 없으면 null 반환.
     *
     * @param token Bearer 토큰 (Bearer 접두사 제외)
     * @return role 문자열 (예: "USER", "ADMIN") 또는 null
     */
    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * 액세스 토큰의 유효성을 검증합니다.
     *
     * @param token 검증할 토큰
     * @return 유효하면 true
     */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("[JWT] 만료된 토큰");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 유효하지 않은 토큰: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
