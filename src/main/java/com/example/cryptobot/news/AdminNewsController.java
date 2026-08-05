package com.example.cryptobot.news;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 뉴스 수집 수동 트리거 API (관리자용).
 * 스케줄러를 기다리지 않고 즉시 수집·진단할 수 있습니다.
 */
@Slf4j
@RestController
@RequestMapping("/admin/news")
@RequiredArgsConstructor
public class AdminNewsController {

    private final NewsService newsService;

    /**
     * RSS + DART 공시를 즉시 수집합니다.
     * <p>
     * 응답 예시:
     * <pre>
     * {
     *   "rss": { "feedsTried": 4, "feedsSucceeded": 3, "feedsFailed": 1, "newsSaved": 12 },
     *   "dart": { "newsSaved": 5 }
     * }
     * </pre>
     *
     * @param target "all"(기본) | "rss" | "dart"
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collect(
            @RequestParam(defaultValue = "all") String target) {

        Map<String, Object> result = new LinkedHashMap<>();
        log.info("[수동트리거] 뉴스 수집 시작 — target={}", target);

        if ("rss".equals(target) || "all".equals(target)) {
            NewsService.CollectResult rss = newsService.triggerRssCollect();
            Map<String, Integer> rssMap = new LinkedHashMap<>();
            rssMap.put("feedsTried",     rss.feedsTried());
            rssMap.put("feedsSucceeded", rss.feedsSucceeded());
            rssMap.put("feedsFailed",    rss.feedsFailed());
            rssMap.put("newsSaved",      rss.newsSaved());
            result.put("rss", rssMap);
        }

        if ("dart".equals(target) || "all".equals(target)) {
            int dartSaved = newsService.triggerDartCollect();
            result.put("dart", Map.of("newsSaved", dartSaved));
        }

        return ResponseEntity.ok(result);
    }
}
