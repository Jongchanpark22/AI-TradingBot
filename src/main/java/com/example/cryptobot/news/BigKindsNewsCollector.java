package com.example.cryptobot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 빅카인즈(한국언론진흥재단) 뉴스 검색 API 수집기.
 * API 문서: https://www.bigkinds.or.kr/v2/news/search.do
 * 환경변수 BIGKINDS_API_KEY 필요.
 *
 * <p>현재 미활성화(유료화로 보류). @Component 제거 → Spring 미등록.
 * 키 발급 후 @Component 복원하면 NewsService에 자동 주입됩니다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class BigKindsNewsCollector implements NewsSource {

    private static final String API_URL = "https://tools.kinds.or.kr/search/news";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bigkinds.api-key:}")
    private String apiKey;

    @Override
    public String sourceName() {
        return "BIGKINDS";
    }

    @Override
    public List<NewsItem> collect(String keyword, int maxResults) {
        List<NewsItem> results = new ArrayList<>();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("빅카인즈 API 키 미설정 (bigkinds.api-key) — 뉴스 수집 건너뜀");
            return results;
        }

        try {
            String today   = LocalDate.now().format(DATE_FMT);
            String weekAgo = LocalDate.now().minusDays(7).format(DATE_FMT);

            Map<String, Object> body = Map.of(
                    "access_key", apiKey,
                    "argument", Map.of(
                            "query", keyword,
                            "published_at", Map.of("from", weekAgo, "until", today),
                            "return_from", 0,
                            "return_size", Math.min(maxResults, 100),
                            "sort", Map.of("date", "desc", "relevance", "desc")
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    upbitRestTemplate.postForEntity(API_URL, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("빅카인즈 API 호출 실패: {} keyword={}", response.getStatusCode(), keyword);
                return results;
            }

            JsonNode root      = objectMapper.readTree(response.getBody());
            JsonNode documents = root.path("return_object").path("documents");

            if (documents.isArray()) {
                for (JsonNode doc : documents) {
                    String url = doc.path("provider_link_page").asText(null);
                    if (url == null || url.isBlank()) continue;

                    NewsItem item = NewsItem.builder()
                            .source(sourceName())
                            .url(url)
                            .title(doc.path("title").asText("제목 없음"))
                            .summary(doc.path("content").asText(null))
                            .publishedAt(parseDateTime(doc.path("published_at").asText(null)))
                            .linkedSymbols("[\"" + keyword + "\"]")
                            .build();

                    results.add(item);
                }
            }
        } catch (Exception e) {
            log.error("빅카인즈 뉴스 수집 오류: keyword={}", keyword, e);
        }

        return results;
    }

    /** "2024-01-15 10:30:00" 또는 "2024-01-15T10:30:00" 형식 파싱 */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(
                    str.replace("T", " ").substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
