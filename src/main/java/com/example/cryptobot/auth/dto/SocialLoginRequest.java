package com.example.cryptobot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 DTO.
 * 앱이 카카오/구글 SDK로 획득한 provider 토큰을 전송합니다.
 *
 * <p>카카오: accessToken 전송.
 * 구글: idToken(ID Token) 전송.</p>
 */
public record SocialLoginRequest(
        @NotBlank(message = "provider 토큰은 필수입니다.")
        String token,

        /** 선택: 앱에서 미리 확보한 닉네임 (없으면 서버가 자동 생성) */
        String nickname
) {}
