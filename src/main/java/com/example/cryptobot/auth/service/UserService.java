package com.example.cryptobot.auth.service;

import com.example.cryptobot.auth.dto.OnboardingRequest;
import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.repository.RefreshTokenRepository;
import com.example.cryptobot.auth.repository.UserRepository;
import com.example.cryptobot.holding.Watchlist;
import com.example.cryptobot.holding.WatchlistRepository;
import com.example.cryptobot.news.NewsPreference;
import com.example.cryptobot.news.NewsPreferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 정보 조회·수정·탈퇴·온보딩 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final WatchlistRepository watchlistRepository;
    private final NewsPreferenceRepository newsPreferenceRepository;
    private final ObjectMapper objectMapper;

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
     * 온보딩 완료 처리.
     * 관심 종목을 Watchlist에 추가하고, 관심 테마를 NewsPreference에 저장한 뒤 onboardedAt을 기록합니다.
     * 이미 온보딩된 경우에도 멱등 처리됩니다(재실행 가능).
     *
     * @param userId  요청 회원 ID
     * @param request 관심 종목(symbols)·테마(themes)
     */
    @Transactional
    public User onboard(Long userId, OnboardingRequest request) {
        User user = getActiveUser(userId);

        // 1. 관심 종목 Watchlist 추가
        if (request.getSymbols() != null && !request.getSymbols().isEmpty()) {
            List<Watchlist> items = request.getSymbols().stream()
                    .map(symbol -> Watchlist.builder()
                            .userId(userId)
                            .symbol(symbol)
                            .build())
                    .toList();
            watchlistRepository.saveAll(items);
            log.info("[온보딩] 관심 종목 {}개 추가: userId={}", items.size(), userId);
        }

        // 2. 관심 테마 NewsPreference 저장
        if (request.getThemes() != null && !request.getThemes().isEmpty()) {
            try {
                String themesJson = objectMapper.writeValueAsString(request.getThemes());
                NewsPreference pref = newsPreferenceRepository.findByUserId(userId)
                        .orElse(NewsPreference.builder().userId(userId).build());
                pref.setThemes(themesJson);
                newsPreferenceRepository.save(pref);
                log.info("[온보딩] 관심 테마 {}개 저장: userId={}", request.getThemes().size(), userId);
            } catch (JsonProcessingException e) {
                log.warn("[온보딩] 테마 직렬화 실패 — 무시하고 계속: {}", e.getMessage());
            }
        }

        // 3. onboardedAt 기록
        user.setOnboardedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        log.info("[온보딩] 완료: userId={}", userId);
        return saved;
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
