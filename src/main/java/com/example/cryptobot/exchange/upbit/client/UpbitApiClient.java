package com.example.cryptobot.exchange.upbit.client;

import com.example.cryptobot.exchange.upbit.config.UpbitApiProperties;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 업비트 API 클라이언트
 * 
 * 기능:
 * - 시세 조회 (Ticker)
 * - 캔들 데이터 조회
 * - 계정 정보 조회
 * - 주문 생성/조회
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
     * @param market BTC-KRW, ETH-KRW, XRP-KRW 등
     * @return 시세 정보
     */
    public UpbitTickerDto getTicker(String market) {
        try {
            String url = properties.getBaseUrl() + "/v1/ticker?markets=" + market;
            UpbitTickerDto[] response = restTemplate.getForObject(url, UpbitTickerDto[].class);
            
            if (response != null && response.length > 0) {
                log.debug("시세 조회 성공: {} = {}", market, response[0].getTradePrice());
                return response[0];
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
     * @param markets BTC-KRW, ETH-KRW 등 쉼표로 구분
     * @return 시세 정보 리스트
     */
    public List<UpbitTickerDto> getTickers(String markets) {
        try {
            String url = properties.getBaseUrl() + "/v1/ticker?markets=" + markets;
            UpbitTickerDto[] response = restTemplate.getForObject(url, UpbitTickerDto[].class);
            
            if (response != null) {
                log.debug("시세 조회 성공: {} 개", response.length);
                return Arrays.asList(response);
            }
            
            return List.of();
        } catch (Exception e) {
            log.error("시세 조회 실패: {}", markets, e);
            return List.of();
        }
    }

    /**
     * 캔들 데이터 조회
     * 
     * @param market BTC-KRW, ETH-KRW 등
     * @param unit 분봉 단위 (1, 5, 15, 30, 60, 240)
     * @param count 조회 개수 (최대 200)
     * @return 캔들 데이터 리스트
     */
    public List<UpbitCandleDto> getCandles(String market, int unit, int count) {
        try {
            String url = String.format(
                    "%s/v1/candles/minutes/%d?market=%s&count=%d",
                    properties.getBaseUrl(),
                    unit,
                    market,
                    count
            );
            
            UpbitCandleDto[] response = restTemplate.getForObject(url, UpbitCandleDto[].class);
            
            if (response != null) {
                log.debug("캔들 조회 성공: {} {}분봉 {} 개", market, unit, response.length);
                return Arrays.asList(response);
            }
            
            return List.of();
        } catch (Exception e) {
            log.error("캔들 조회 실패: {} {}분봉", market, unit, e);
            return List.of();
        }
    }

    // ===== 인증이 필요한 API =====

    /**
     * 계정 정보 조회
     * 
     * @return 계정 정보 리스트 (화폐별)
     */
    public List<UpbitAccountDto> getAccounts() {
        try {
            String authorizationToken = generateAuthToken("");
            String url = properties.getBaseUrl() + "/v1/accounts";
            
            UpbitAccountDto[] response = restTemplate
                    .getForObject(
                            url,
                            UpbitAccountDto[].class,
                            createHeaders(authorizationToken)
                    );
            
            if (response != null) {
                log.debug("계정 조회 성공: {} 개 화폐", response.length);
                return Arrays.asList(response);
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
     * @param market BTC-KRW, ETH-KRW 등
     * @param side "bid" (매수) 또는 "ask" (매도)
     * @param ordType "limit" (지정가) 또는 "market" (시장가)
     * @param volume 주문 수량
     * @param price 주문 가격 (지정가일 때 필수)
     * @return 주문 정보
     */
    public UpbitOrderDto createOrder(
            String market,
            String side,
            String ordType,
            BigDecimal volume,
            BigDecimal price) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("market", market);
            params.put("side", side);
            params.put("ord_type", ordType);
            params.put("volume", volume.toPlainString());
            
            if ("limit".equals(ordType) && price != null) {
                params.put("price", price.toPlainString());
            }
            
            String query = buildQueryString(params);
            String authorizationToken = generateAuthToken(query);
            String url = properties.getBaseUrl() + "/v1/orders?" + query;
            
            UpbitOrderDto response = restTemplate.postForObject(
                    url,
                    null,
                    UpbitOrderDto.class,
                    createHeaders(authorizationToken)
            );
            
            if (response != null) {
                log.info("주문 생성 성공: {} {} {} {}", market, side, volume, price);
                return response;
            }
            
            return null;
        } catch (Exception e) {
            log.error("주문 생성 실패: {} {} {} {}", market, side, volume, price, e);
            return null;
        }
    }

    /**
     * 주문 취소
     * 
     * @param uuid 주문 UUID
     * @return 주문 정보
     */
    public UpbitOrderDto cancelOrder(String uuid) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);
            
            String query = buildQueryString(params);
            String authorizationToken = generateAuthToken(query);
            String url = properties.getBaseUrl() + "/v1/order?" + query;
            
            // RestTemplate delete 메서드는 반환값이 없으므로, GET으로 변경
            UpbitOrderDto response = restTemplate.getForObject(
                    url,
                    UpbitOrderDto.class,
                    createHeaders(authorizationToken)
            );
            
            if (response != null) {
                log.info("주문 취소 성공: {}", uuid);
                return response;
            }
            
            return null;
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", uuid, e);
            return null;
        }
    }

    // ===== 헬퍼 메서드 =====

    /**
     * JWT 토큰 생성 (업비트 인증용)
     */
    private String generateAuthToken(String query) throws Exception {
        Key key = Keys.hmacShaKeyFor(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_key", properties.getAccessKey());
        payload.put("nonce", UUID.randomUUID().toString());
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("query_hash_alg", "SHA512");
        
        if (query != null && !query.isEmpty()) {
            payload.put("query_hash", hashQueryString(query));
        }
        
        return Jwts.builder()
                .setHeader(headers)
                .setClaims(payload)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 쿼리 스트링 생성
     */
    private String buildQueryString(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(key).append("=").append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * 쿼리 스트링 해시 (SHA512)
     */
    private String hashQueryString(String query) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("쿼리 해시 생성 실패", e);
            return "";
        }
    }

    /**
     * HTTP 헤더 생성
     */
    private Map<String, String> createHeaders(String authorizationToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + authorizationToken);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}

