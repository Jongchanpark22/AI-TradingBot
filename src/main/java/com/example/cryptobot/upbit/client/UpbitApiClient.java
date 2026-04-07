package com.example.cryptobot.upbit.client;

import com.example.cryptobot.upbit.config.UpbitApiProperties;
import com.example.cryptobot.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.upbit.dto.UpbitOrderChanceDto;
import com.example.cryptobot.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.upbit.dto.UpbitTickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitApiClient {

    private final UpbitApiProperties properties;
    private final RestTemplate restTemplate;

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
        } catch (HttpStatusCodeException e) {
            log.error("시세 조회 실패 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("시세 조회 실패: {}", market, e);
            return null;
        }
    }

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
        } catch (HttpStatusCodeException e) {
            log.error("다중 시세 조회 실패 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return List.of();
        } catch (Exception e) {
            log.error("다중 시세 조회 실패: {}", markets, e);
            return List.of();
        }
    }

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
        } catch (HttpStatusCodeException e) {
            log.error("캔들 조회 실패 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return List.of();
        } catch (Exception e) {
            log.error("캔들 조회 실패: {} {}분봉", market, unit, e);
            return List.of();
        }
    }

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
        } catch (HttpStatusCodeException e) {
            log.error("계정 조회 실패 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return List.of();
        } catch (Exception e) {
            log.error("계정 조회 실패", e);
            return List.of();
        }
    }

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

            if ("limit".equals(ordType)) {
                if (volume == null || price == null) {
                    throw new IllegalArgumentException("지정가 주문은 volume, price가 모두 필요합니다.");
                }
                body.put("volume", volume.toPlainString());
                body.put("price", price.toPlainString());

            } else if ("price".equals(ordType) && "bid".equals(side)) {
                if (price == null) {
                    throw new IllegalArgumentException("시장가 매수는 주문 금액(price)이 필요합니다.");
                }
                body.put("price", price.toPlainString());

            } else if ("market".equals(ordType) && "ask".equals(side)) {
                if (volume == null) {
                    throw new IllegalArgumentException("시장가 매도는 volume이 필요합니다.");
                }
                body.put("volume", volume.toPlainString());

            } else {
                throw new IllegalArgumentException("지원하지 않는 주문 타입 조합입니다. side=" + side + ", ordType=" + ordType);
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
                log.info("주문 생성 성공: market={}, side={}, ordType={}, uuid={}",
                        market, side, ordType, result.getUuid());
            }
            return result;

        } catch (HttpStatusCodeException e) {
            log.error("업비트 실주문 실패 status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("주문 생성 실패: market={}, side={}, ordType={}, volume={}, price={}",
                    market, side, ordType, volume, price, e);
            return null;
        }
    }

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

            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("업비트 주문 취소 실패 status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", uuid, e);
            return null;
        }
    }

    public UpbitOrderChanceDto getOrderChance(String market) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("market", market);

            String queryStringForHash = buildQueryStringForHash(params);
            String token = generateAuthToken(queryStringForHash);

            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/v1/orders/chance")
                    .queryParam("market", market)
                    .toUriString();

            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(token));

            ResponseEntity<UpbitOrderChanceDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UpbitOrderChanceDto.class
            );

            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("주문 가능 정보 조회 실패 status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("주문 가능 정보 조회 실패: {}", market, e);
            return null;
        }
    }

    public UpbitOrderDto createOrderTest(
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

            if ("limit".equals(ordType)) {
                if (volume == null || price == null) {
                    throw new IllegalArgumentException("지정가 테스트 주문은 volume, price가 모두 필요합니다.");
                }
                body.put("volume", volume.toPlainString());
                body.put("price", price.toPlainString());

            } else if ("price".equals(ordType) && "bid".equals(side)) {
                if (price == null) {
                    throw new IllegalArgumentException("시장가 매수 테스트 주문은 주문 금액(price)이 필요합니다.");
                }
                body.put("price", price.toPlainString());

            } else if ("market".equals(ordType) && "ask".equals(side)) {
                if (volume == null) {
                    throw new IllegalArgumentException("시장가 매도 테스트 주문은 volume이 필요합니다.");
                }
                body.put("volume", volume.toPlainString());

            } else {
                throw new IllegalArgumentException("지원하지 않는 테스트 주문 타입 조합입니다. side=" + side + ", ordType=" + ordType);
            }

            String queryStringForHash = buildQueryStringForHash(body);
            String token = generateAuthToken(queryStringForHash);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, createAuthHeaders(token));

            ResponseEntity<UpbitOrderDto> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/v1/orders/test",
                    HttpMethod.POST,
                    entity,
                    UpbitOrderDto.class
            );

            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("업비트 테스트 주문 실패 status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("주문 생성 테스트 실패: market={}, side={}, ordType={}, volume={}, price={}",
                    market, side, ordType, volume, price, e);
            return null;
        }
    }

    private String generateAuthToken(String queryStringForHash) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("access_key", properties.getAccessKey());
            payload.put("nonce", UUID.randomUUID().toString());

            if (queryStringForHash != null && !queryStringForHash.isBlank()) {
                payload.put("query_hash", hashQueryString(queryStringForHash));
                payload.put("query_hash_alg", "SHA512");
            }

            String headerJson = "{\"alg\":\"HS512\",\"typ\":\"JWT\"}";
            String payloadJson = toJson(payload);

            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            String message = encodedHeader + "." + encodedPayload;
            String signature = hmacSha512(message, properties.getSecretKey());

            return message + "." + signature;
        } catch (Exception e) {
            log.error("업비트 JWT 생성 실패", e);
            throw new IllegalStateException("업비트 JWT 생성 실패", e);
        }
    }

    private String hmacSha512(String message, String secretKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"
        );
        mac.init(secretKeySpec);
        byte[] signatureBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(signatureBytes);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String toJson(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + toJsonValue(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private String buildQueryStringForHash(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

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

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}