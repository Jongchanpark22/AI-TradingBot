package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.CompanyFinancials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Optional;

/**
 * AI 기업 리포트 생성 서비스.
 *
 * <p>DART 재무 데이터를 수집 → Java에서 지표 계산 → Gemini가 한국어 서술 생성.
 * LLM은 숫자를 생성하지 않으며, 미리 계산된 수치를 바탕으로 설명만 합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final DartFinancialClient dartFinancialClient;
    private final LlmClient llmClient;

    /**
     * 기업 코드로 AI 리포트를 생성합니다. 직전 사업연도를 자동으로 선택합니다.
     *
     * @param corpCode DART 기업 고유번호 (8자리, 예: 00126380)
     * @return 생성된 리포트, 데이터 없으면 empty
     */
    public Optional<AiReportResponse> generateReport(String corpCode) {
        // 직전 사업연도 (현재 연도 -1)
        int targetYear = Year.now().getValue() - 1;
        return generateReport(corpCode, targetYear);
    }

    /**
     * 기업 코드와 사업연도를 지정하여 AI 리포트를 생성합니다.
     *
     * @param corpCode     DART 기업 고유번호 (8자리)
     * @param businessYear 사업연도 (예: 2023)
     * @return 생성된 리포트, 데이터 없으면 empty
     */
    public Optional<AiReportResponse> generateReport(String corpCode, int businessYear) {
        log.info("AI 기업 리포트 생성 시작: corpCode={}, year={}", corpCode, businessYear);

        // 1단계: DART에서 재무 데이터 수집 + Java 지표 계산
        Optional<CompanyFinancials> financialsOpt =
                dartFinancialClient.fetchFinancials(corpCode, businessYear);

        if (financialsOpt.isEmpty()) {
            log.warn("재무 데이터 없음 — 리포트 생성 불가: corpCode={}, year={}", corpCode, businessYear);
            return Optional.empty();
        }

        CompanyFinancials financials = financialsOpt.get();

        // 2단계: Gemini에게 숫자를 전달하고 한국어 서술만 요청
        String prompt = buildPrompt(financials);
        String narrative = llmClient.generate(prompt);

        // API 키 미설정 시 기본 서술
        if (narrative == null || narrative.isBlank()) {
            narrative = "LLM 서비스 미설정으로 서술 생성 불가. " +
                        "재무 지표는 위 요약을 참고하세요. (gemini.api-key 설정 필요)";
        }

        AiReportResponse report = AiReportResponse.builder()
                .corpCode(corpCode)
                .corpName(financials.getCorpName())
                .businessYear(businessYear)
                .financials(financials)
                .narrative(narrative)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("AI 기업 리포트 생성 완료: corpCode={}, year={}", corpCode, businessYear);
        return Optional.of(report);
    }

    /**
     * Gemini에게 보낼 프롬프트를 생성합니다.
     * 원칙: 모든 숫자를 컨텍스트로 전달, LLM은 설명만 생성, 숫자·목표주가·매수의견 금지.
     */
    private String buildPrompt(CompanyFinancials financials) {
        return String.format("""
                당신은 기업 재무 분석 전문가입니다. 아래 재무 데이터를 바탕으로 한국어 분석 서술을 작성하세요.

                중요 제약 조건:
                1. 아래 제공된 숫자만 사용하세요. 새로운 숫자를 생성하거나 추정하지 마세요.
                2. 목표주가, 적정주가, 매수/매도/중립 의견을 제시하지 마세요.
                3. "~할 것으로 예상됩니다", "향후 성장이 기대됩니다" 등 투자 권유성 표현을 사용하지 마세요.
                4. 재무 지표가 의미하는 바를 쉽게 설명하고, 동종업계 평균과 비교하지 마세요 (데이터가 없으므로).
                5. 결론 마지막에 반드시 "본 내용은 투자 권유가 아닙니다"를 명시하세요.

                서술 구성 (각 항목 2~3문장):
                1. 매출 현황 및 전년 대비 변화
                2. 수익성 (영업이익률, 순이익률)
                3. 재무 건전성 (부채비율, 자본 구조)
                4. 자본 효율성 (ROE)
                5. 종합 요약 (면책 문구 포함)

                %s
                """, financials.toPromptContext());
    }
}
