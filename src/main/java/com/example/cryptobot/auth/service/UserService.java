package com.example.cryptobot.auth.service;

import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.repository.RefreshTokenRepository;
import com.example.cryptobot.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 조회·수정·탈퇴 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원 정보 조회. 탈퇴 회원은 예외.
     */
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    /**
     * 닉네임 수정.
     */
    @Transactional
    public User updateNickname(Long userId, String nickname) {
        User user = getActiveUser(userId);
        user.setNickname(nickname);
        return userRepository.save(user);
    }

    /**
     * 비밀번호 변경.
     *
     * @throws IllegalArgumentException 현재 비밀번호 불일치
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getActiveUser(userId);

        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("[User] 비밀번호 변경 완료: userId={}", userId);
    }

    /**
     * 회원 탈퇴 — soft delete + 개인정보 익명화.
     * 리프레시 토큰을 모두 revoke 하여 즉시 로그인 불가 상태로 만듭니다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = getActiveUser(userId);

        // 개인정보 익명화
        user.setStatus(User.Status.WITHDRAWN);
        user.setEmail("withdrawn_" + userId + "@deleted");
        user.setPasswordHash(null);
        user.setNickname("탈퇴한 회원");
        userRepository.save(user);

        // 리프레시 토큰 전체 revoke
        refreshTokenRepository.revokeAllByUserId(userId);

        log.info("[User] 회원 탈퇴 처리 완료: userId={}", userId);
    }
}
