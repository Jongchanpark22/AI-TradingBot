package com.example.cryptobot.auth.social;

/**
 * 소셜 제공자로부터 검증·추출한 사용자 정보.
 *
 * @param providerUserId 제공자 고유 ID (카카오: Long 문자열, 구글: sub)
 * @param email          제공자가 제공한 이메일 (없을 수 있음)
 * @param nickname       제공자가 제공한 닉네임 (없을 수 있음)
 */
public record SocialUserInfo(
        String providerUserId,
        String email,
        String nickname
) {}
