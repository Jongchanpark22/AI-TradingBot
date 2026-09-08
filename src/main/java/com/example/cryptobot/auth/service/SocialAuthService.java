package com.example.cryptobot.auth.service;

import com.example.cryptobot.auth.dto.AuthResponse;
import com.example.cryptobot.auth.entity.AuthProvider;
import com.example.cryptobot.auth.entity.RefreshToken;
import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.jwt.JwtProvider;
import com.example.cryptobot.auth.repository.AuthProviderRepository;
import com.example.cryptobot.auth.repository.RefreshTokenRepository;
import com.example.cryptobot.auth.repository.UserRepository;
import com.example.cryptobot.auth.social.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 소셜 로그인 서비스.
 *
 * <p>제공자 토큰 검증 후 User 조회/생성 → JWT 발급 흐름을 처리합니다.
 * 계정 통합: 동일 이메일로 이미 이메일 가입한 User 가 있으면 AuthProvider 를 연결합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final AuthProviderRepository authProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    @Value("${jwt.refresh-token-expiry-days:14}")
    private int refreshTokenExpiryDays;

    /**
     * 소셜 로그인 — User 조회/생성 후 JWT 발급.
     *
     * @param provider     인증 제공자
     * @param userInfo     제공자로부터 검증된 사용자 정보
     * @param reqNickname  앱이 전달한 닉네임 (없으면 자동 생성)
     */
    @Transactional
    public AuthResponse loginOrRegister(AuthProvider.Provider provider,
                                        SocialUserInfo userInfo,
                                        String reqNickname) {
        // 1. 기존 소셜 계정 연결 조회
        return authProviderRepository
                .findByProviderAndProviderUserId(provider, userInfo.providerUserId())
                .map(ap -> {
                    // 기존 사용자 로그인
                    User user = userRepository.findById(ap.getUserId())
                            .filter(u -> u.getStatus() == User.Status.ACTIVE)
                            .orElseThrow(() -> new IllegalArgumentException("탈퇴한 회원입니다."));
                    log.info("[SocialAuth] 기존 계정 로그인: provider={}, userId={}", provider, user.getId());
                    return issueTokens(user);
                })
                .orElseGet(() -> registerNewSocialUser(provider, userInfo, reqNickname));
    }

    /** 소셜 최초 로그인 — User + AuthProvider 신규 생성 (계정 통합 포함). */
    private AuthResponse registerNewSocialUser(AuthProvider.Provider provider,
                                               SocialUserInfo userInfo,
                                               String reqNickname) {
        // 동일 이메일 기존 계정이 있으면 계정 통합
        User user = (userInfo.email() != null)
                ? userRepository.findByEmail(userInfo.email())
                        .filter(u -> u.getStatus() == User.Status.ACTIVE)
                        .orElseGet(() -> createUser(userInfo, reqNickname))
                : createUser(userInfo, reqNickname);

        // AuthProvider 연결
        authProviderRepository.save(AuthProvider.builder()
                .userId(user.getId())
                .provider(provider)
                .providerUserId(userInfo.providerUserId())
                .build());

        log.info("[SocialAuth] 소셜 계정 등록 완료: provider={}, userId={}", provider, user.getId());
        return issueTokens(user);
    }

    private User createUser(SocialUserInfo userInfo, String reqNickname) {
        String nickname = resolveNickname(reqNickname, userInfo.nickname());
        return userRepository.save(User.builder()
                .email(userInfo.email())
                .nickname(nickname)
                .build());
    }

    /** 닉네임 우선순위: 요청값 → 제공자 닉네임 → 자동 생성 */
    private String resolveNickname(String reqNickname, String providerNickname) {
        if (reqNickname != null && !reqNickname.isBlank()) return reqNickname.trim();
        if (providerNickname != null && !providerNickname.isBlank()) return providerNickname.trim();
        return "사용자" + System.currentTimeMillis() % 100000;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshValue = jwtProvider.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(AuthService.sha256(refreshValue))
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .build());
        return new AuthResponse(accessToken, refreshValue, AuthResponse.UserInfo.from(user));
    }
}
