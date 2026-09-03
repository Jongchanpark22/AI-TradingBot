package com.example.cryptobot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DART Open API 클라이언트.
 * 금융감독원 전자공시 시스템 공시 목록을 조회합니다.
 * API 문서: https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiCd=20190305
 * 환경변수 DART_API_KEY 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartApiClient {

    private static final String DART_LIST_URL = "https://opendart.fss.or.kr/api/list.json";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DART_VIEW_URL = "https://dart.fss.or.kr/dsaf001/main.do?rceptNo=";

    @Qualifier("upbitRestTemplate")
    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dart.api-key:}")
    private String apiKey;

    /**
     * 특정 날짜 범위의 공시 목록을 조회합니다.
     *
     * @param from  조회 시작일
     * @param until 조회 종료일
     * @return 공시 JSON 배열 노드
     */
    public List<DartRawItem> fetchDisclosures(LocalDate from, LocalDate until) {
        List<DartRawItem> results = new ArrayList<>();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DART API 키 미설정 (dart.api-key) — 공시 수집 건너뜀");
            return results;
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(DART_LIST_URL)
                    .queryParam("crtfc_key", apiKey)
                    .queryParam("bgn_de", from.format(DATE_FMT))
                    .queryParam("end_de", until.format(DATE_FMT))
                    .queryParam("page_count", 100)
                    .queryParam("sort", "date")
                    .queryParam("sort_mth", "desc")
                    .build(true)
                    .toUri();

            ResponseEntity<String> response = upbitRestTemplate.getForEntity(uri, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DART API 호출 실패: {}", response.getStatusCode());
                return results;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();

            if (!"000".equals(status)) {
                log.warn("DART API 오류 응답: status={}, message={}", status, root.path("message").asText());
                return results;
            }

            JsonNode list = root.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    // stock_code: 상장 종목은 6자리, 비상장은 공백 문자열
                    String stockCode = item.path("stock_code").asText("").trim();
                    results.add(new DartRawItem(
                            item.path("rcept_no").asText(),
                            item.path("corp_code").asText(),
                            item.path("corp_name").asText(),
                            item.path("report_nm").asText(),
                            item.path("rcept_dt").asText(),
                            DART_VIEW_URL + item.path("rcept_no").asText(),
                            stockCode.isEmpty() ? null : stockCode
                    ));
                }
            }
        } catch (Exception e) {
            log.error("DART 공시 조회 오류", e);
        }

        return results;
    }

    /**
     * DART API 응답 항목 DTO.
     * stockCode: 상장 종목의 6자리 종목코드 (비상장이면 null).
     */
    public record DartRawItem(
            String rceptNo,
            String corpCode,
            String corpName,
            String reportNm,
            String rceptDt,
            String url,
            String stockCode
    ) {}
}
