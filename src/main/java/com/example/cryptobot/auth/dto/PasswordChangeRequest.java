package com.example.cryptobot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 DTO.
 */
public record PasswordChangeRequest(
        @NotBlank
        String currentPassword,

        @NotBlank @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        String newPassword
) {}
