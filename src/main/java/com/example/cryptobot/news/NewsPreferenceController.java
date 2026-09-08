package com.example.cryptobot.news;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 뉴스 테마·종목 구독 설정 API.
 */
@RestController
@RequestMapping("/news/preferences")
@RequiredArgsConstructor
public class NewsPreferenceController {

    private final NewsPreferenceRepository preferenceRepository;

    /**
     * 내 뉴스 구독 설정 조회.
     */
    @GetMapping
    public ResponseEntity<NewsPreference> getPreferences(@AuthenticationPrincipal Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(NewsPreference.builder().build()));
    }

    /**
     * 뉴스 구독 설정 저장·갱신.
     * body 예: {"themes":"[\"금리\",\"FOMC\"]","symbols":"[\"KRW-BTC\",\"005930\"]"}
     */
    @PutMapping
    @Transactional
    public ResponseEntity<NewsPreference> upsertPreferences(
            @AuthenticationPrincipal Long userId,
            @RequestBody NewsPreference request) {
        NewsPreference pref = preferenceRepository.findByUserId(userId)
                .orElse(NewsPreference.builder().userId(userId).build());
        pref.setThemes(request.getThemes());
        pref.setSymbols(request.getSymbols());
        return ResponseEntity.ok(preferenceRepository.save(pref));
    }
}
