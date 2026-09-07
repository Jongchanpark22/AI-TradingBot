package com.example.cryptobot.auth.controller;

import com.example.cryptobot.auth.dto.AuthResponse;
import com.example.cryptobot.auth.dto.PasswordChangeRequest;
import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 회원 정보 API — 조회·수정·비밀번호 변경·탈퇴.
 * 모든 엔드포인트는 인증 필요.
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 내 정보 조회.
     */
    @GetMapping
    public ResponseEntity<AuthResponse.UserInfo> getMe(@AuthenticationPrincipal Long userId) {
        User user = userService.getActiveUser(userId);
        return ResponseEntity.ok(AuthResponse.UserInfo.from(user));
    }

    /**
     * 닉네임 수정.
     */
    @PatchMapping
    public ResponseEntity<AuthResponse.UserInfo> updateMe(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {

        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User updated = userService.updateNickname(userId, nickname.trim());
        return ResponseEntity.ok(AuthResponse.UserInfo.from(updated));
    }

    /**
     * 비밀번호 변경.
     */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request) {

        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * 회원 탈퇴 — soft delete + 개인정보 익명화.
     */
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
