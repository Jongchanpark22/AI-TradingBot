package com.example.cryptobot.auth.dto;

import com.example.cryptobot.auth.entity.User;

/**
 * 로그인·가입·토큰 갱신 응답 DTO.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            Long id,
            String email,
            String nickname,
            String tier,
            boolean onboarded
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getTier().name(),
                    user.getOnboardedAt() != null
            );
        }
    }
}
