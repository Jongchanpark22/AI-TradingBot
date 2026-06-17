package com.example.cryptobot.exchange.upbit.client;

import com.example.cryptobot.exchange.upbit.config.UpbitApiProperties;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitMarketDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 업비트 API 클라이언트
 *
 * 기능:
 * - 시세 조회 (Ticker)
 * - 캔들 데이터 조회
 * - 계정 정보 조회
 * - 주문 생성/취소
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitApiClient {

    private final UpbitApiProperties properties;
    private final RestTemplate restTemplate;

    // ===== 공개 API =====

    /**
     * 시세 정보 조회
     *
     * @param market KRW-BTC, KRW-ETH, KRW-XRP 등
     */
    public UpbitTickerDto getTicker(String market) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/ticker")
                    .queryParam("markets", market)
                    .toUriString();

            ResponseEntity<UpbitTickerDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    UpbitTickerDto[].class
            );

            UpbitTickerDto[] body = response.getBody();
            if (body != null && body.length > 0) {
                log.debug("시세 조회 성공: {} = {}", market, body[0].getTradePrice());
                return body[0];
            }

            log.warn("시세 정보 없음: {}", market);
            return null;
        } catch (Exception e) {
            log.error("시세 조회 실패: {}", market, e);
            return null;
        }
    }

    /**
     * 여러 시세 정보 조회
     *
     * @param markets KRW-BTC,KRW-ETH 등 쉼표 구분
     */
    public List<UpbitTickerDto> getTickers(String markets) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/ticker")
                    .queryParam("markets", markets)
                    .toUriString();

            ResponseEntity<UpbitTickerDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    UpbitTickerDto[].class
            );

            UpbitTickerDto[] body = response.getBody();
            if (body != null) {
                log.debug("다중 시세 조회 성공: {} 개", body.length);
                return Arrays.asList(body);
            }

            return List.of();
        } catch (Exception e) {
            log.error("다중 시세 조회 실패: {}", markets, e);
            return List.of();
        }
    }

    /**
     * 캔들 데이터 조회
     *
     * @param market KRW-BTC, KRW-ETH 등
     * @param unit 분봉 단위 (1, 5, 15, 30, 60, 240)
     * @param count 조회 개수
     */
    public List<UpbitCandleDto> getCandles(String market, int unit, int count) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/candles/minutes/" + unit)
                    .queryParam("market", market)
                    .queryParam("count", count)
                    .toUriString();

            ResponseEntity<UpbitCandleDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    UpbitCandleDto[].class
            );

            UpbitCandleDto[] body = response.getBody();
            if (body != null) {
                log.debug("캔들 조회 성공: {} {}분봉 {} 개", market, unit, body.length);
                return Arrays.asList(body);
            }

            return List.of();
        } catch (Exception e) {
            log.error("캔들 조회 실패: {} {}분봉", market, unit, e);
            return List.of();
        }
    }

    /**
     * 전체 KRW 마켓 목록 조회
     * GET /v1/market/all
     */
    public List<String> getAllKrwMarkets() {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/market/all")
                    .queryParam("isDetails", false)
                    .toUriString();

            ResponseEntity<UpbitMarketDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    UpbitMarketDto[].class
            );

            UpbitMarketDto[] body = response.getBody();
            if (body == null) return List.of();

            List<String> markets = Arrays.stream(body)
                    .map(UpbitMarketDto::getMarket)
                    .filter(m -> m != null && m.startsWith("KRW-"))
                    .toList();

            log.debug("KRW 마켓 조회: {} 개", markets.size());
            return markets;
        } catch (Exception e) {
            log.error("마켓 목록 조회 실패", e);
            return List.of();
        }
    }

    // ===== 인증 필요 API =====

    /**
     * 계정 정보 조회
     */
    public List<UpbitAccountDto> getAccounts() {
        try {
            String token = generateAuthToken(null);
            String url = properties.getBaseUrl() + "/v1/accounts";

            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(token));

            ResponseEntity<UpbitAccountDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UpbitAccountDto[].class
            );

            UpbitAccountDto[] body = response.getBody();
            if (body != null) {
                log.debug("계정 조회 성공: {} 개 화폐", body.length);
                return Arrays.asList(body);
            }

            return List.of();
        } catch (Exception e) {
            log.error("계정 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 주문 생성
     *
     * @param market KRW-BTC, KRW-ETH 등
     * @param side bid(매수) 또는 ask(매도)
     * @param ordType limit(지정가) 또는 market(시장가)
     * @param volume 주문 수량
     * @param price 주문 가격
     */
    public UpbitOrderDto createOrder(
            String market,
            String side,
            String ordType,
            BigDecimal volume,
            BigDecimal price
    ) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("market", market);
            body.put("side", side);
            body.put("ord_type", ordType);

            if (volume != null) {
                body.put("volume", volume.toPlainString());
            }
            if (price != null) {
                body.put("price", price.toPlainString());
            }

            String queryStringForHash = buildQueryStringForHash(body);
            String token = generateAuthToken(queryStringForHash);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, createAuthHeaders(token));

            ResponseEntity<UpbitOrderDto> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/v1/orders",
                    HttpMethod.POST,
                    entity,
                    UpbitOrderDto.class
            );

            UpbitOrderDto result = response.getBody();
            if (result != null) {
                log.info("주문 생성 성공: {} {} {} {}", market, side, volume, price);
            }
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // ask(매도) 주문의 잔고 부족 / 최소금액 미달 오류는 호출자가 처리하도록 rethrow
            if ("ask".equals(side)) {
                String body = e.getResponseBodyAsString();
                if (body.contains("insufficient_funds_ask") || body.contains("under_min_total_market_ask")) {
                    throw e;
                }
            }
            log.error("주문 생성 실패: {} {} {} {}", market, side, volume, price, e);
            return null;
        } catch (Exception e) {
            log.error("주문 생성 실패: {} {} {} {}", market, side, volume, price, e);
            return null;
        }
    }

    /**
     * 주문 상태 조회
     *
     * @param uuid 업비트 주문 UUID
     */
    public UpbitOrderDto getOrderStatus(String uuid) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("uuid", uuid);

            String queryStringForHash = buildQueryStringForHash(params);
            String token = generateAuthToken(queryStringForHash);

            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/order")
                    .queryParam("uuid", uuid)
                    .toUriString();

            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(token));

            ResponseEntity<UpbitOrderDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, UpbitOrderDto.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("주문 상태 조회 실패: uuid={}", uuid, e);
            return null;
        }
    }

    /**
     * 주문 취소
     *
     * @param uuid 업비트 주문 UUID
     */
    public UpbitOrderDto cancelOrder(String uuid) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("uuid", uuid);

            String queryStringForHash = buildQueryStringForHash(params);
            String token = generateAuthToken(queryStringForHash);

            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/order")
                    .queryParam("uuid", uuid)
                    .toUriString();

            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(token));

            ResponseEntity<UpbitOrderDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    UpbitOrderDto.class
            );

            UpbitOrderDto result = response.getBody();
            if (result != null) {
                log.info("주문 취소 성공: {}", uuid);
            }
            return result;
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", uuid, e);
            return null;
        }
    }

    // ===== 헬퍼 메서드 =====

    /**
     * 업비트 인증용 JWT 생성
     */
    private String generateAuthToken(String queryStringForHash) {
        Key key = Keys.hmacShaKeyFor(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));

        Map<String, Object> claims = new HashMap<>();
        claims.put("access_key", properties.getAccessKey());
        claims.put("nonce", UUID.randomUUID().toString());

        if (queryStringForHash != null && !queryStringForHash.isBlank()) {
            claims.put("query_hash", hashQueryString(queryStringForHash));
            claims.put("query_hash_alg", "SHA512");
        }

        return Jwts.builder()
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * query_hash 생성용 쿼리 문자열
     * URL 인코딩 없이 원문 기준으로 생성
     */
    private String buildQueryStringForHash(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * SHA-512 해시
     */
    private String hashQueryString(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("query_hash 생성 실패", e);
            throw new IllegalStateException("query_hash 생성 실패", e);
        }
    }

    /**
     * 인증 헤더 생성
     */
    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}