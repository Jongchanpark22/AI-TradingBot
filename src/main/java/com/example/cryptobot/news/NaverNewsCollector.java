package com.example.cryptobot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 네이버 검색 API — 뉴스 수집기.
 * 무료 (일 25,000회), 키워드 기반 검색.
 * 환경변수 NAVER_CLIENT_ID, NAVER_CLIENT_SECRET 필요.
 * API 문서: https://developers.naver.com/docs/serviceapi/search/news/news.md
 *
 * <p>현재 미활성화(유료화 가능성으로 보류). @Component 제거 → Spring 미등록.
 * 키 발급 후 @Component 복원하면 NewsService에 자동 주입됩니다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class NaverNewsCollector implements NewsSource {

    private static final String API_URL = "https://openapi.naver.com/v1/search/news.json";
    private static final DateTimeFormatter NAVER_DT_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${naver.client-id:}")
    private String clientId;

    @Value("${naver.client-secret:}")
    private String clientSecret;

    @Override
    public String sourceName() {
        return "NAVER_NEWS";
    }

    @Override
    public List<NewsItem> collect(String keyword, int maxResults) {
        List<NewsItem> results = new ArrayList<>();

        if (clientId == null || clientId.isBlank()) {
            log.warn("네이버 API 키 미설정 (naver.client-id) — 뉴스 수집 건너뜀");
            return results;
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                    .queryParam("query", keyword)
                    .queryParam("display", Math.min(maxResults, 100))
                    .queryParam("sort", "date")
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    upbitRestTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("네이버 뉴스 API 호출 실패: {} keyword={}", response.getStatusCode(), keyword);
                return results;
            }

            JsonNode root  = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String link = item.path("link").asText(null);
                    if (link == null || link.isBlank()) continue;

                    // HTML 태그 제거
                    String title = item.path("title").asText("").replaceAll("<[^>]+>", "");
                    String desc  = item.path("description").asText("").replaceAll("<[^>]+>", "");

                    NewsItem newsItem = NewsItem.builder()
                            .source(sourceName())
                            .url(link)
                            .title(title)
                            .summary(desc.isBlank() ? null : desc)
                            .publishedAt(parseNaverDate(item.path("pubDate").asText(null)))
                            .linkedSymbols("[\"" + keyword + "\"]")
                            .build();

                    results.add(newsItem);
                }
            }
        } catch (Exception e) {
            log.error("네이버 뉴스 수집 오류: keyword={}", keyword, e);
        }

        return results;
    }

    /** "Sat, 15 Jan 2024 10:30:00 +0900" 형식 파싱 */
    private LocalDateTime parseNaverDate(String str) {
        if (str == null || str.isBlank()) return LocalDateTime.now();
        try {
            return ZonedDateTime.parse(str, NAVER_DT_FMT).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
