package com.example.cryptobot.report.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Java에서 계산한 기업 재무 지표.
 * 모든 숫자는 DART 원본 데이터 기반으로 서비스 레이어에서 계산됩니다.
 * LLM은 이 데이터를 받아 설명만 생성하며, 숫자를 직접 만들지 않습니다.
 */
@Getter
@Builder
public class CompanyFinancials {

    /** 기업 코드 (DART corp_code) */
    private String corpCode;

    /** 기업명 */
    private String corpName;

    /** 사업연도 */
    private int businessYear;

    // ── 손익계산서 ──────────────────────────────────────────────
    /** 매출액 당기 (원) */
    private long revenue;

    /** 매출액 전기 (원) */
    private long revenuePrev;

    /** 매출액 전전기 (원) */
    private long revenuePrevPrev;

    /** 영업이익 당기 (원) */
    private long operatingIncome;

    /** 영업이익 전기 (원) */
    private long operatingIncomePrev;

    /** 영업이익 전전기 (원) */
    private long operatingIncomePrevPrev;

    /** 당기순이익 당기 (원) */
    private long netIncome;

    /** 당기순이익 전기 (원) */
    private long netIncomePrev;

    /** 당기순이익 전전기 (원) */
    private long netIncomePrevPrev;

    // ── 재무상태표 ──────────────────────────────────────────────
    /** 자산총계 (원) */
    private long totalAssets;

    /** 부채총계 (원) */
    private long totalLiabilities;

    /** 자본총계 (원) */
    private long totalEquity;

    // ── 계산 지표 (서비스 레이어에서 산출) ─────────────────────
    /** 영업이익률 (%) — null: 매출액 0 */
    private Double operatingMargin;

    /** 순이익률 (%) — null: 매출액 0 */
    private Double netMargin;

    /** 부채비율 (%) — null: 자본총계 0 */
    private Double debtRatio;

    /** ROE (%) — null: 자본총계 0 */
    private Double roe;

    /** 매출액 전년 대비 증감률 (%) — null: 전기 매출 0 */
    private Double revenueGrowth;

    /** 수집된 원본 계정 목록 (프롬프트 컨텍스트용) */
    private List<FinancialAccount> rawAccounts;

    /**
     * 프롬프트에 포함할 3개년 재무 요약 텍스트를 반환합니다.
     * 당기/전기/전전기를 모두 포함하여 LLM이 추세를 서술할 수 있도록 합니다.
     */
    public String toPromptContext() {
        int yr = businessYear;
        return String.format("""
                [%s (%s) 3개년 재무 현황]

                ▶ 매출액
                  %d년(당기)   : %s원
                  %d년(전기)   : %s원
                  %d년(전전기) : %s원
                  당기 전년 대비 증감률: %s

                ▶ 영업이익
                  %d년(당기)   : %s원  (영업이익률: %s)
                  %d년(전기)   : %s원  (영업이익률: %s)
                  %d년(전전기) : %s원  (영업이익률: %s)

                ▶ 당기순이익
                  %d년(당기)   : %s원  (순이익률: %s)
                  %d년(전기)   : %s원  (순이익률: %s)
                  %d년(전전기) : %s원  (순이익률: %s)

                ▶ 재무상태표 (당기 %d년)
                  자산총계: %s원
                  부채총계: %s원
                  자본총계: %s원
                  부채비율: %s  /  ROE: %s
                """,
                corpName, corpCode,
                yr,   fmtAmt(revenue),
                yr-1, fmtAmt(revenuePrev),
                yr-2, fmtAmt(revenuePrevPrev),
                fmtRate(revenueGrowth),
                yr,   fmtAmt(operatingIncome),   fmtRate(calcRate(operatingIncome, revenue)),
                yr-1, fmtAmt(operatingIncomePrev), fmtRate(calcRate(operatingIncomePrev, revenuePrev)),
                yr-2, fmtAmt(operatingIncomePrevPrev), fmtRate(calcRate(operatingIncomePrevPrev, revenuePrevPrev)),
                yr,   fmtAmt(netIncome),   fmtRate(calcRate(netIncome, revenue)),
                yr-1, fmtAmt(netIncomePrev), fmtRate(calcRate(netIncomePrev, revenuePrev)),
                yr-2, fmtAmt(netIncomePrevPrev), fmtRate(calcRate(netIncomePrevPrev, revenuePrevPrev)),
                yr,
                fmtAmt(totalAssets),
                fmtAmt(totalLiabilities),
                fmtAmt(totalEquity),
                fmtRate(debtRatio), fmtRate(roe)
        );
    }

    private static String fmtAmt(long amount) {
        if (amount == 0) return "데이터 없음";
        return String.format("%,d", amount);
    }

    private static String fmtRate(Double rate) {
        return rate == null ? "산출 불가" : String.format("%.2f%%", rate);
    }

    private static Double calcRate(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 100.0 / denominator;
    }
}
