package com.example.cryptobot.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Google Gemini Flash API 클라이언트.
 * 환경변수 GEMINI_API_KEY 필요. 미설정 시 경고 후 빈 문자열 반환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLlmClient implements LlmClient {

    /** Gemini API 엔드포인트 (무료 티어 사용 가능) */
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final RestTemplate geminiRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    /** 기동 시 Gemini API 키 로드 여부를 확인합니다. 값은 뒤 4자리만 표시합니다. */
    @PostConstruct
    public void logKeyStatus() {
        String trimmed = apiKey != null ? apiKey.trim() : "";
        if (trimmed.isBlank()) {
            log.warn("[Gemini] API 키 미설정 — GEMINI_API_KEY 환경변수 또는 gemini.api-key 프로퍼티를 확인하세요.");
        } else {
            String masked = "***" + trimmed.substring(Math.max(0, trimmed.length() - 4));
            log.info("[Gemini] API 키 로드 완료: {}", masked);
        }
    }

    @Override
    public String generate(String prompt) {
        // 공백 포함 오입력 방지를 위해 trim() 처리
        String trimmedKey = apiKey != null ? apiKey.trim() : "";
        if (trimmedKey.isBlank()) {
            log.warn("Gemini API 키 미설정 (gemini.api-key) — LLM 호출 건너뜀");
            return "";
        }

        try {
            String url = String.format(GEMINI_URL, model, trimmedKey);

            // 요청 바디: contents[parts[text]] + generationConfig
            Map<String, Object> body = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{Map.of("text", prompt)})
                    },
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "maxOutputTokens", 2048
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = geminiRestTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Gemini API 호출 실패: {}", response.getStatusCode());
                return "";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            // candidates[0].content.parts[0].text
            return root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");

        } catch (Exception e) {
            log.error("Gemini API 호출 오류", e);
            return "";
        }
    }
}
