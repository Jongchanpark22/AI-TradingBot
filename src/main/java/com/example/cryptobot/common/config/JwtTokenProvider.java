//package com.example.cryptobot.common.config;
//
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.security.Key;
//import java.util.Date;
//
///**
// * JWT 토큰 제공자
// *
// * 기능:
// * - JWT 토큰 생성
// * - JWT 토큰 검증
// * - JWT 토큰에서 정보 추출
// */
//@Slf4j
//@Component
//public class JwtTokenProvider {
//
//    private final Key key;
//    private final long validityInMilliseconds;
//
//    public JwtTokenProvider(
//            @Value("${jwt.secret:your-secret-key-must-be-at-least-256-bits-long-for-hs256}") String secret,
//            @Value("${jwt.expiration:86400000}") long validityInMilliseconds) {
//
//        this.key = Keys.hmacShaKeyFor(secret.getBytes());
//        this.validityInMilliseconds = validityInMilliseconds;
//    }
//
//    /**
//     * JWT 토큰 생성
//     *
//     * @param username 사용자명
//     * @return JWT 토큰
//     */
//    public String generateToken(String username) {
//        Date now = new Date();
//        Date expiresAt = new Date(now.getTime() + validityInMilliseconds);
//
//        return Jwts.builder()
//                .subject(username)
//                .issuedAt(now)
//                .expiration(expiresAt)
//                .signWith(key)
//                .compact();
//    }
//
//    /**
//     * JWT 토큰에서 사용자명 추출
//     *
//     * @param token JWT 토큰
//     * @return 사용자명
//     */
//    public String getUsernameFromToken(String token) {
//        try {
//            Claims claims = Jwts.parserBuilder()
//                    .setSigningKey(key)
//                    .build()
//                    .parseClaimsJws(token)
//                    .getBody();
//            return claims.getSubject();
//        } catch (Exception e) {
//            log.error("토큰에서 사용자명 추출 실패", e);
//            return null;
//        }
//    }
//
//    /**
//     * JWT 토큰 유효성 검증
//     *
//     * @param token JWT 토큰
//     * @return 유효 여부
//     */
//    public boolean validateToken(String token) {
//        try {
//            Jwts.parserBuilder()
//                    .setSigningKey(key)
//                    .build()
//                    .parseClaimsJws(token);
//            return true;
//        } catch (Exception e) {
//            log.error("토큰 유효성 검증 실패: {}", e.getMessage());
//            return false;
//        }
//    }
//}
//
