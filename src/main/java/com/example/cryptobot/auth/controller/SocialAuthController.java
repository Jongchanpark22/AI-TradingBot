package com.example.cryptobot.auth.controller;

import com.example.cryptobot.auth.dto.AuthResponse;
import com.example.cryptobot.auth.dto.SocialLoginRequest;
import com.example.cryptobot.auth.entity.AuthProvider;
import com.example.cryptobot.auth.service.SocialAuthService;
import com.example.cryptobot.auth.social.GoogleTokenVerifier;
import com.example.cryptobot.auth.social.KakaoTokenVerifier;
import com.example.cryptobot.auth.social.SocialUserInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 소셜 로그인 API.
 *
 * <p>앱이 카카오/구글 SDK로 획득한 provider 토큰을 받아
 * 서버에서 제공자 API로 검증 후 우리 JWT를 발급합니다.</p>
 */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final KakaoTokenVerifier kakaoVerifier;
    private final GoogleTokenVerifier googleVerifier;
    private final SocialAuthService socialAuthService;

    /**
     * 카카오 소셜 로그인.
     * body.token: 카카오 SDK가 발급한 access_token
     */
    @PostMapping("/kakao")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        SocialUserInfo userInfo = kakaoVerifier.verify(request.token());
        AuthResponse response = socialAuthService.loginOrRegister(
                AuthProvider.Provider.KAKAO, userInfo, request.nickname());
        return ResponseEntity.ok(response);
    }

    /**
     * 구글 소셜 로그인.
     * body.token: 구글 Sign-In SDK가 발급한 ID 토큰
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        SocialUserInfo userInfo = googleVerifier.verify(request.token());
        AuthResponse response = socialAuthService.loginOrRegister(
                AuthProvider.Provider.GOOGLE, userInfo, request.nickname());
        return ResponseEntity.ok(response);
    }

    /**
     * 애플 소셜 로그인 — iOS 출시 시 활성화 예정. 현재 뼈대만.
     */
    @PostMapping("/apple")
    public ResponseEntity<Void> appleLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.status(501).build(); // Not Implemented
    }
}
