package com.example.cryptobot.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 토스증권 Open API 클라이언트.
 *
 * <p>OAuth2 Client Credentials 방식으로 토큰을 발급받아 재사용합니다.
 * 토큰은 만료 60초 전에 자동 갱신됩니다.</p>
 *
 * <p>지원 지수: KOSPI, KOSDAQ
 * (토스증권 OpenAPI는 해외 지수(NASDAQ·S&P500 등)를 공식 미지원 — 국내 지수만 제공)</p>
 *
 * <p>엔드포인트: GET /api/v1/market-indicators/{symbol} (심볼별 단건 조회)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossSecuritiesApiClient {

    /** 조회할 국내 지수 심볼 목록. 해외 지수는 토스증권 API 미지원. */
    private static final List<String> INDEX_SYMBOLS = List.of("KOSPI", "KOSDAQ");

    private final TossSecuritiesProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 인메모리 토큰 캐시 */
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    // ─── 지수 조회 ────────────────────────────────────────────────────────────

    /**
     * 국내 주요 지수(KOSPI, KOSDAQ) 시세를 조회합니다.
     *
     * <p>토스증권 Open API는 해외 지수(NASDAQ 등)를 지원하지 않습니다.
     * NASDAQ 데이터가 필요하면 별도 데이터 소스 계약 필요.</p>
     *
     * @return 지수 시세 목록. API 키 미설정 또는 오류 시 빈 목록 반환
     */
    public List<IndexPrice> fetchIndices() {
        if (!isConfigured()) {
            log.debug("[토스증권] API 키 미설정 — 지수 조회 건너뜀");
            return List.of();
        }

        String token = getAccessToken();
        if (token == null) return List.of();

        List<IndexPrice> results = new ArrayList<>();
        for (String symbol : INDEX_SYMBOLS) {
            fetchSingleIndex(symbol, token).ifPresent(results::add);
        }
        return results;
    }

    /**
     * 단일 지수 시세 조회.
     * 응답 필드명이 API 버전마다 다를 수 있어 여러 후보를 순서대로 시도합니다.
     */
    private java.util.Optional<IndexPrice> fetchSingleIndex(String symbol, String token) {
        try {
            String url = properties.getBaseUrl() + "/api/v1/market-indicators/" + symbol;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[토스증권] {} 지수 조회 실패: {}", symbol, response.getStatusCode());
                return java.util.Optional.empty();
            }

            JsonNode node = objectMapper.readTree(response.getBody());
            // 응답이 배열이면 첫 번째 요소 사용 (API 버전별 차이 대응)
            JsonNode data = node.isArray() ? node.get(0) : node;
            if (data == null) return java.util.Optional.empty();

            double price = resolveDouble(data, "price", "lastPrice", "closePrice", "currentPrice");
            double changeRate = resolveDouble(data, "changeRate", "changeRatio", "priceChangeRate");

            log.debug("[토스증권] {} 지수 조회 완료: price={}, changeRate={}", symbol, price, changeRate);
            return java.util.Optional.of(new IndexPrice(symbol, price, changeRate));

        } catch (Exception e) {
            log.error("[토스증권] {} 지수 조회 오류: {}", symbol, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * JsonNode 에서 후보 필드명 순서대로 첫 번째로 존재하는 double 값을 반환합니다.
     * 모두 없으면 0.0 반환.
     */
    private double resolveDouble(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode n = node.get(field);
            if (n != null && !n.isNull()) {
                return n.asDouble();
            }
        }
        return 0.0;
    }

    // ─── OAuth2 토큰 관리 ────────────────────────────────────────────────────

    /**
     * 유효한 액세스 토큰을 반환합니다.
     * 캐시된 토큰이 만료 60초 이내이면 새로 발급합니다.
     */
    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.token();
        }
        return refreshToken();
    }

    /**
     * 토스증권 OAuth2 토큰을 발급받아 캐시에 저장합니다.
     */
    private String refreshToken() {
        try {
            String url = properties.getBaseUrl() + "/oauth2/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", properties.getClientId());
            body.add("client_secret", properties.getClientSecret());

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[토스증권] 토큰 발급 실패: {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String token = root.path("access_token").asText();
            long expiresIn = root.path("expires_in").asLong(3600);

            CachedToken cachedToken = new CachedToken(token, Instant.now().plusSeconds(expiresIn));
            tokenCache.set(cachedToken);

            log.info("[토스증권] 토큰 발급 완료: 만료까지 {}초", expiresIn);
            return token;
        } catch (Exception e) {
            log.error("[토스증권] 토큰 발급 오류", e);
            return null;
        }
    }

    private boolean isConfigured() {
        return properties.getClientId() != null && !properties.getClientId().isBlank()
                && properties.getClientSecret() != null && !properties.getClientSecret().isBlank();
    }

    // ─── 내부 record ─────────────────────────────────────────────────────────

    /**
     * 지수 시세 DTO.
     *
     * @param symbol     지수 심볼 (KOSPI, KOSDAQ)
     * @param lastPrice  현재 지수값
     * @param changeRate 전일 대비 등락률 (%)
     */
    public record IndexPrice(String symbol, double lastPrice, double changeRate) {}

    /**
     * 인메모리 토큰 캐시 항목.
     */
    private record CachedToken(String token, Instant expiresAt) {
        /** 만료 60초 전이면 true */
        boolean isExpiringSoon() {
            return Instant.now().isAfter(expiresAt.minusSeconds(60));
        }
    }
}
