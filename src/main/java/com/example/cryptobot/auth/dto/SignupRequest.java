package com.example.cryptobot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 회원가입 요청 DTO.
 */
public record SignupRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        String password,

        @NotBlank @Size(min = 2, max = 50)
        String nickname,

        /** 동의한 약관 버전 (예: "1.0") */
        @NotBlank
        String termsVersion
) {}
