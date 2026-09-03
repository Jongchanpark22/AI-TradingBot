package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.CompanyFinancials;
import com.example.cryptobot.report.dto.FinancialAccount;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DART 단일 기업 재무제표 API 클라이언트.
 * fnlttSinglAcnt.json 엔드포인트를 통해 손익계산서·재무상태표를 수집하고
 * 재무 지표를 Java에서 직접 계산합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartFinancialClient {

    /** DART 단일회사 주요계정 API */
    private static final String DART_FINANCE_URL =
            "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json";

    /** DART 기업 정보 조회 API */
    private static final String DART_COMPANY_URL =
            "https://opendart.fss.or.kr/api/company.json";

    /** 재무상태표 구분 코드 */
    private static final String BS = "BS";

    /** 사업보고서 코드 */
    private static final String ANNUAL_REPORT = "11011";

    /** 연결재무제표 우선 (CFS), 없으면 개별(OFS) 사용 */
    private static final String CFS = "CFS";

    @Qualifier("upbitRestTemplate")
    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dart.api-key:}")
    private String apiKey;

    /**
     * 기업 코드와 사업연도를 입력받아 재무 지표를 계산하여 반환합니다.
     *
     * @param corpCode    DART 기업 고유번호 (8자리)
     * @param businessYear 사업연도 (예: 2023)
     * @return 계산된 재무 지표, API 키 미설정 또는 오류 시 empty
     */
    public Optional<CompanyFinancials> fetchFinancials(String corpCode, int businessYear) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DART API 키 미설정 (dart.api-key) — 재무 데이터 수집 건너뜀");
            return Optional.empty();
        }

        List<FinancialAccount> accounts = fetchAccounts(corpCode, businessYear);
        if (accounts.isEmpty()) {
            log.warn("재무 데이터 없음: corpCode={}, year={}", corpCode, businessYear);
            return Optional.empty();
        }

        String corpName = resolveCorpName(corpCode);
        CompanyFinancials financials = buildFinancials(corpCode, corpName, businessYear, accounts);
        return Optional.of(financials);
    }

    /**
     * DART API에서 원본 계정 목록을 조회합니다.
     */
    private List<FinancialAccount> fetchAccounts(String corpCode, int businessYear) {
        List<FinancialAccount> results = new ArrayList<>();
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(DART_FINANCE_URL)
                    .queryParam("crtfc_key", apiKey)
                    .queryParam("corp_code", corpCode)
                    .queryParam("bsns_year", businessYear)
                    .queryParam("reprt_code", ANNUAL_REPORT)
                    .build(true)
                    .toUri();

            ResponseEntity<String> response = upbitRestTemplate.getForEntity(uri, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DART 재무 API 호출 실패: {}", response.getStatusCode());
                return results;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();

            if (!"000".equals(status)) {
                log.warn("DART 재무 API 오류: status={}, message={}",
                        status, root.path("message").asText());
                return results;
            }

            JsonNode list = root.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    results.add(new FinancialAccount(
                            item.path("fs_div").asText(),       // CFS(연결) / OFS(개별)
                            item.path("sj_div").asText(),
                            item.path("account_nm").asText(),
                            item.path("thstrm_amount").asText(),
                            item.path("frmtrm_amount").asText(),
                            item.path("bfefrmtrm_amount").asText()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("DART 재무 데이터 수집 오류: corpCode={}", corpCode, e);
        }
        return results;
    }

    /**
     * 기업 코드로 기업명을 조회합니다. 실패 시 corp_code 그대로 반환.
     */
    private String resolveCorpName(String corpCode) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(DART_COMPANY_URL)
                    .queryParam("crtfc_key", apiKey)
                    .queryParam("corp_code", corpCode)
                    .build(true)
                    .toUri();

            ResponseEntity<String> response = upbitRestTemplate.getForEntity(uri, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return corpCode;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String name = root.path("corp_name").asText();
            return name.isBlank() ? corpCode : name;
        } catch (Exception e) {
            log.warn("기업명 조회 실패: corpCode={}", corpCode);
            return corpCode;
        }
    }

    /**
     * 원본 계정 목록에서 재무 지표를 계산하여 CompanyFinancials를 생성합니다.
     * 모든 산술 연산은 이 메서드에서 수행됩니다 (LLM에 숫자 생성 위임 금지).
     *
     * <p>DART API는 동일 계정을 CFS(연결)·OFS(개별)로 이중 반환하므로
     * CFS를 우선 선택합니다. CFS 없는 경우(소규모 기업) OFS 사용.</p>
     */
    private CompanyFinancials buildFinancials(
            String corpCode, String corpName, int businessYear,
            List<FinancialAccount> accounts) {

        // CFS(연결) 우선, 없으면 OFS(개별) 폴백
        List<FinancialAccount> preferred = filterByFsDiv(accounts, CFS);
        if (preferred.isEmpty()) {
            preferred = accounts;
        }

        // 매출액 — IS에 있음. 금융업 등은 "영업수익"이므로 둘 다 시도
        long revenue = findByExact(preferred, "IS", "매출액");
        if (revenue == 0) revenue = findByExact(preferred, "IS", "영업수익");

        long revenuePrev = findPrevByExact(preferred, "IS", "매출액");
        if (revenuePrev == 0) revenuePrev = findPrevByExact(preferred, "IS", "영업수익");

        // 영업이익 — IS에 있음
        long operatingIncome = findByExact(preferred, "IS", "영업이익");

        // 당기순이익 — IS 또는 CIS에 있고, "(손실)" 변형이 있으므로 contains 매칭
        // "법인세비용차감전순이익"과 구분하기 위해 "당기순이익"으로 시작하는 행 우선
        long netIncome = findNetIncome(preferred);

        // 재무상태표 — BS에 있음
        long totalAssets      = findByExact(preferred, BS, "자산총계");
        long totalLiabilities = findByExact(preferred, BS, "부채총계");
        long totalEquity      = findByExact(preferred, BS, "자본총계");

        Double operatingMargin = divide(operatingIncome * 100.0, revenue);
        Double netMargin       = divide(netIncome * 100.0, revenue);
        Double debtRatio       = divide(totalLiabilities * 100.0, totalEquity);
        Double roe             = divide(netIncome * 100.0, totalEquity);
        Double revenueGrowth   = revenuePrev == 0 ? null
                : ((revenue - revenuePrev) * 100.0) / revenuePrev;

        return CompanyFinancials.builder()
                .corpCode(corpCode)
                .corpName(corpName)
                .businessYear(businessYear)
                .revenue(revenue)
                .revenuePrev(revenuePrev)
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .debtRatio(debtRatio)
                .roe(roe)
                .revenueGrowth(revenueGrowth)
                .rawAccounts(accounts)
                .build();
    }

    /**
     * fs_div(CFS/OFS)로 계정 목록을 필터링합니다.
     */
    private List<FinancialAccount> filterByFsDiv(List<FinancialAccount> accounts, String fsDiv) {
        return accounts.stream()
                .filter(a -> fsDiv.equals(a.fsDiv()))
                .toList();
    }

    /**
     * sjDiv·accountNm 정확 일치로 당기 금액을 찾습니다.
     */
    private long findByExact(List<FinancialAccount> accounts, String sjDiv, String accountNm) {
        return accounts.stream()
                .filter(a -> sjDiv.equals(a.sjDiv()) && accountNm.equals(a.accountNm()))
                .mapToLong(FinancialAccount::thstrmLong)
                .findFirst()
                .orElse(0L);
    }

    /**
     * sjDiv·accountNm 정확 일치로 전기 금액을 찾습니다.
     */
    private long findPrevByExact(List<FinancialAccount> accounts, String sjDiv, String accountNm) {
        return accounts.stream()
                .filter(a -> sjDiv.equals(a.sjDiv()) && accountNm.equals(a.accountNm()))
                .mapToLong(FinancialAccount::frmtrmLong)
                .findFirst()
                .orElse(0L);
    }

    /**
     * 당기순이익을 IS/CIS 모두에서 찾습니다.
     * DART는 기업에 따라 IS 또는 CIS에 기재하며, "당기순이익(손실)" 변형이 있습니다.
     * "법인세비용차감전순이익"과 구분하기 위해 "당기순이익"으로 시작하는 행을 우선합니다.
     */
    private long findNetIncome(List<FinancialAccount> accounts) {
        // 1순위: IS에서 "당기순이익"으로 시작하는 계정
        return accounts.stream()
                .filter(a -> ("IS".equals(a.sjDiv()) || "CIS".equals(a.sjDiv()))
                        && a.accountNm() != null
                        && a.accountNm().startsWith("당기순이익"))
                .mapToLong(FinancialAccount::thstrmLong)
                .findFirst()
                // 2순위: contains 폴백 (IS 우선, CIS 후순위 순서로 stream이 정렬되어 있으면 자연스럽게 처리)
                .orElseGet(() -> accounts.stream()
                        .filter(a -> ("IS".equals(a.sjDiv()) || "CIS".equals(a.sjDiv()))
                                && a.accountNm() != null
                                && a.accountNm().contains("당기순이익"))
                        .mapToLong(FinancialAccount::thstrmLong)
                        .findFirst()
                        .orElse(0L));
    }

    /** 분모가 0이면 null 반환 (산출 불가 표시용). */
    private static Double divide(double numerator, long denominator) {
        return denominator == 0 ? null : numerator / denominator;
    }
}
