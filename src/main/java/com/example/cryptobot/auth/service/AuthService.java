package com.example.cryptobot.auth.service;

import com.example.cryptobot.auth.dto.AuthResponse;
import com.example.cryptobot.auth.dto.LoginRequest;
import com.example.cryptobot.auth.dto.SignupRequest;
import com.example.cryptobot.auth.entity.AuthProvider;
import com.example.cryptobot.auth.entity.RefreshToken;
import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.jwt.JwtProvider;
import com.example.cryptobot.auth.repository.AuthProviderRepository;
import com.example.cryptobot.auth.repository.RefreshTokenRepository;
import com.example.cryptobot.auth.repository.UserRepository;
import com.example.cryptobot.common.apiPayload.ErrorCode;
import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 이메일 인증 서비스.
 * 가입, 로그인, 토큰 갱신, 로그아웃을 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthProviderRepository authProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry-days:14}")
    private int refreshTokenExpiryDays;

    /**
     * 이메일 회원가입.
     *
     * @throws BusinessException 이메일 중복 시 EMAIL_DUPLICATE(409)
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }

        User user = userRepository.save(User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .termsVersion(request.termsVersion())
                .build());

        authProviderRepository.save(AuthProvider.builder()
                .userId(user.getId())
                .provider(AuthProvider.Provider.EMAIL)
                .build());

        log.info("[Auth] 이메일 가입 완료: userId={}, email={}", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    /**
     * 이메일 로그인.
     *
     * @throws BusinessException 이메일 없음 또는 비밀번호 불일치 시 INVALID_CREDENTIALS(401)
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("[Auth] 로그인 성공: userId={}", user.getId());
        return issueTokens(user);
    }

    /**
     * 리프레시 토큰으로 액세스 토큰 재발급(rotation).
     * 이전 리프레시 토큰은 revoke 하고 새 리프레시 토큰을 발급합니다.
     *
     * @throws BusinessException 토큰 없음·만료·revoke됨 시 EXPIRED_TOKEN(401)
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        String hash = sha256(refreshTokenValue);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        // rotation: 기존 토큰 revoke
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.WITHDRAWN_USER));

        log.info("[Auth] 토큰 갱신: userId={}", user.getId());
        return issueTokens(user);
    }

    /**
     * 로그아웃 — 해당 사용자의 리프레시 토큰을 모두 revoke 합니다.
     */
    @Transactional
    public void logout(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("[Auth] 로그아웃: userId={}, revoke된 토큰 수={}", userId, revoked);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /** 액세스 + 리프레시 토큰을 발급하고 AuthResponse 를 반환합니다. */
    private AuthResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name());

        String refreshValue = jwtProvider.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshValue))
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .build());

        return new AuthResponse(accessToken, refreshValue, AuthResponse.UserInfo.from(user));
    }

    /** 문자열을 SHA-256 해시(hex)로 변환합니다. */
    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
