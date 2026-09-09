package com.example.cryptobot.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 설정 API.
 * 테마·알림 종류별 on/off를 조회·수정합니다.
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserSettingsRepository settingsRepository;

    /**
     * 내 설정 조회.
     * 설정이 없으면 기본값(테마=SYSTEM, 모든 알림 ON)을 즉시 생성·반환합니다.
     */
    @GetMapping
    @Transactional
    public UserSettings getSettings(@AuthenticationPrincipal Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> settingsRepository.save(
                        UserSettings.builder().userId(userId).build()
                ));
    }

    /**
     * 내 설정 부분 업데이트.
     * null 필드는 기존 값을 유지합니다.
     *
     * <p>body 예: {"theme":"DARK","newsAlertEnabled":false}</p>
     */
    @PatchMapping
    @Transactional
    public UserSettings updateSettings(
            @AuthenticationPrincipal Long userId,
            @RequestBody SettingsRequest request) {

        UserSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> UserSettings.builder().userId(userId).build());

        if (request.getTheme() != null) {
            settings.setTheme(request.getTheme());
        }
        if (request.getPriceAlertEnabled() != null) {
            settings.setPriceAlertEnabled(request.getPriceAlertEnabled());
        }
        if (request.getNewsAlertEnabled() != null) {
            settings.setNewsAlertEnabled(request.getNewsAlertEnabled());
        }
        if (request.getReportAlertEnabled() != null) {
            settings.setReportAlertEnabled(request.getReportAlertEnabled());
        }

        return settingsRepository.save(settings);
    }
}
