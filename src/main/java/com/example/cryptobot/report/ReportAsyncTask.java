package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.CompanyFinancials;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 리포트 비동기 생성 작업.
 *
 * <p>@Async 메서드는 같은 빈 내에서 직접 호출하면 프록시를 타지 않아 동기 실행됩니다.
 * AiReportService와 분리된 별도 빈으로 두어 Spring 프록시를 통해 비동기 실행을 보장합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAsyncTask {

    private final DartFinancialClient dartFinancialClient;
    private final LlmClient llmClient;
    private final SavedReportRepository savedReportRepository;
    private final ObjectMapper objectMapper;

    /**
     * 백그라운드에서 DART 재무 수집 → Gemini 서술 생성 → SavedReport 업데이트.
     * 성공: status=DONE, 실패: status=FAILED + errorMessage.
     *
     * @param reportId     미리 생성된 SavedReport ID (status=GENERATING)
     * @param corpCode     DART 기업 고유번호
     * @param businessYear 사업연도
     */
    @Async("reportExecutor")
    public CompletableFuture<Void> generate(Long reportId, String corpCode, int businessYear) {
        log.info("[비동기리포트] ▶ 시작 — 스레드: {}, reportId={}, corpCode={}, year={}",
                Thread.currentThread().getName(), reportId, corpCode, businessYear);
        try {
            // 1단계: DART 재무 수집 + 지표 계산
            log.info("[비동기리포트] [1/3] DART 재무 수집 시작: reportId={}", reportId);
            Optional<CompanyFinancials> financialsOpt =
                    dartFinancialClient.fetchFinancials(corpCode, businessYear);
            if (financialsOpt.isEmpty()) {
                log.warn("[비동기리포트] [1/3] DART 재무 데이터 없음 — FAILED 처리: reportId={}", reportId);
                markFailed(reportId, "DART 재무 데이터 없음: corpCode=" + corpCode + ", year=" + businessYear);
                return CompletableFuture.completedFuture(null);
            }
            CompanyFinancials financials = financialsOpt.get();
            log.info("[비동기리포트] [1/3] DART 완료: 기업명={}, reportId={}", financials.getCorpName(), reportId);

            // 2단계: Gemini 서술 생성
            log.info("[비동기리포트] [2/3] Gemini 호출 시작: reportId={}", reportId);
            String narrative = llmClient.generate(buildPrompt(financials));
            if (narrative == null || narrative.isBlank()) {
                log.warn("[비동기리포트] [2/3] Gemini 응답 없음 (빈 문자열) — 폴백 메시지 사용: reportId={}", reportId);
                narrative = "LLM 미응답 — gemini.api-key 및 모델명을 확인하세요.";
            } else {
                log.info("[비동기리포트] [2/3] Gemini 완료: 응답 {}자, reportId={}", narrative.length(), reportId);
            }

            // 3단계: SavedReport DONE 업데이트
            log.info("[비동기리포트] [3/3] DB 상태 업데이트: reportId={}", reportId);
            String contentJson  = objectMapper.writeValueAsString(financials);
            String sourceMeta   = String.format("DART 사업보고서 %d년도 | 완료: %s",
                    businessYear, LocalDateTime.now());

            SavedReport report = savedReportRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalStateException("SavedReport 없음: " + reportId));
            report.setTargetName(financials.getCorpName());
            report.setNarrative(narrative);
            report.setContentJson(contentJson);
            report.setSourceMeta(sourceMeta);
            report.setStatus(SavedReport.Status.DONE);
            savedReportRepository.save(report);

            log.info("[비동기리포트] ✔ 완료: reportId={}, corpCode={}", reportId, corpCode);

        } catch (Exception e) {
            log.error("[비동기리포트] ✘ 예외 발생: reportId={}, corpCode={}, 원인: {}",
                    reportId, corpCode, e.getMessage(), e);
            markFailed(reportId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    private void markFailed(Long reportId, String errorMessage) {
        try {
            savedReportRepository.findById(reportId).ifPresent(r -> {
                r.setStatus(SavedReport.Status.FAILED);
                r.setErrorMessage(errorMessage);
                savedReportRepository.save(r);
            });
        } catch (Exception e) {
            log.error("[비동기리포트] FAILED 상태 저장 실패: reportId={}", reportId, e);
        }
    }

    /**
     * Gemini 프롬프트 생성.
     * 원칙: Java 계산 숫자만 제공, LLM은 설명만 생성, 숫자 생성·투자 권유 금지.
     */
    private String buildPrompt(CompanyFinancials financials) {
        return """
            당신은 개인 투자자가 기업 재무를 쉽게 이해하도록 돕는 재무 분석가입니다.
            독자는 주식을 하지만 재무제표를 깊이 볼 줄은 모르는 비전문가입니다.
            아래 재무 데이터만 근거로, 전문적이면서도 쉬운 분석 리포트를 작성하세요.

            [절대 규칙]
            1. 아래 제공된 숫자만 사용. 새 숫자 생성·추정 금지. 계산이 필요하면 제공된 값으로만.
            2. 특정 지표가 0이거나 없으면 지어내지 말고 "데이터 없음"으로 명시.
            3. 목표주가·적정주가·매수/매도/중립 의견 금지.
            4. "성장이 기대됩니다" 같은 미래 예측·투자권유성 표현 금지. 과거·현재 사실만.
            5. 동종업계 비교 금지(데이터 없음).
            6. 모든 판단은 반드시 구체적 숫자에 근거(예: "영업이익률이 13.1%로 전년보다 하락").
            7. 마지막에 "본 내용은 투자 권유가 아닌 정보 제공입니다" 명시.
            8. 3개년 데이터가 제공되므로 반드시 3개년 추세를 분석할 것.
               - 적자→흑자 전환, 급증, 급감 같은 변화는 구체적 수치와 함께 명시.
               - 단순히 당기 숫자만 나열하지 말고 전기·전전기 대비 흐름을 서술할 것.

            [작성 원칙 — 전문성 + 쉬움]
            - 각 지표는 "수치 → 전년 대비 변화 → 그게 뜻하는 바"를 함께 서술.
            - 전문용어는 한 번씩 쉬운 말로 풀어줌.
            - 좋은 점과 우려되는 점을 균형 있게.
            - 막연한 미사여구 금지, 데이터에 닿는 구체적 문장만.

            [리포트 구성]
            ## 한눈에 보기
            - 이 기업의 재무 상태를 2~3문장으로 요약(핵심 숫자 포함).

            ## 매출
            - 3개년(전전기→전기→당기) 매출 흐름과 증감률. 방향성이 의미하는 바.

            ## 수익성
            - 영업이익·순이익의 3개년 변화. 마진이 무엇을 뜻하는지 쉬운 설명.
            - 적자→흑자 전환이나 급격한 변화가 있으면 반드시 강조.
            - 매출 증가에도 마진이 줄었다면 원가 압박 등 사실 범위에서 서술.

            ## 재무 건전성
            - 부채비율·자본구조. 이 수준이 부담이 큰지 안정적인지 맥락(단정 대신 사실 기반).

            ## 자본 효율성
            - ROE 수치와 의미. 자기자본 대비 수익성을 쉬운 말로.

            ## 주목할 점
            - 데이터에서 드러나는 강점 1~2개와 유의할 점 1~2개를 균형 있게.

            ## 종합
            - 전체를 2~3문장으로 정리 + 면책 문구.

            [재무 데이터]
            """ + financials.toPromptContext();
    }
}
