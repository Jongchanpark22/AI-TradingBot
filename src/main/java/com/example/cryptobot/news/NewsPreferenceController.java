package com.example.cryptobot.news;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 뉴스 테마·종목 구독 설정 API.
 * userId는 3차 인증 연결 전까지 1L 고정.
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
    public ResponseEntity<NewsPreference> getPreferences() {
        return preferenceRepository.findByUserId(1L)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(NewsPreference.builder().build()));
    }

    /**
     * 뉴스 구독 설정 저장·갱신.
     * body 예: {"themes":"[\"금리\",\"FOMC\"]","symbols":"[\"KRW-BTC\",\"005930\"]"}
     */
    @PutMapping
    @Transactional
    public ResponseEntity<NewsPreference> upsertPreferences(@RequestBody NewsPreference request) {
        NewsPreference pref = preferenceRepository.findByUserId(1L)
                .orElse(NewsPreference.builder().userId(1L).build());
        pref.setThemes(request.getThemes());
        pref.setSymbols(request.getSymbols());
        return ResponseEntity.ok(preferenceRepository.save(pref));
    }
}
