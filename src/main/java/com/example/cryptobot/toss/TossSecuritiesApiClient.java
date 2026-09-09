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
import org.springframework.web.util.UriComponentsBuilder;

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
 * <p>지원 지수: KOSPI, KOSDAQ (업비트 BTC는 UpbitMarketService에서 별도 조회)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossSecuritiesApiClient {

    private final TossSecuritiesProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 인메모리 토큰 캐시 */
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    // ─── 지수 조회 ────────────────────────────────────────────────────────────

    /**
     * 국내 주요 지수(KOSPI, KOSDAQ) 시세를 조회합니다.
     *
     * @return 지수 시세 목록. API 키 미설정 또는 오류 시 빈 목록 반환
     */
    public List<IndexPrice> fetchIndices() {
        if (!isConfigured()) {
            log.debug("토스증권 API 키 미설정 (TOSS_CLIENT_ID/TOSS_CLIENT_SECRET) — 지수 조회 건너뜀");
            return List.of();
        }

        List<IndexPrice> results = new ArrayList<>();
        try {
            String token = getAccessToken();
            if (token == null) return results;

            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/api/v1/prices")
                    .queryParam("symbols", "KOSPI,KOSDAQ")
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("토스증권 지수 조회 실패: {}", response.getStatusCode());
                return results;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            // 응답이 배열인 경우와 단일 객체인 경우 모두 처리
            JsonNode items = root.isArray() ? root : root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    results.add(new IndexPrice(
                            item.path("symbol").asText(),
                            item.path("lastPrice").asDouble(),
                            item.path("changeRate").asDouble(0.0)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("토스증권 지수 조회 오류", e);
        }
        return results;
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
                log.warn("토스증권 토큰 발급 실패: {}", response.getStatusCode());
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
            log.error("토스증권 토큰 발급 오류", e);
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
