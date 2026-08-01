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
    /** 매출액 (원) */
    private long revenue;

    /** 전기 매출액 (원) */
    private long revenuePrev;

    /** 영업이익 (원) */
    private long operatingIncome;

    /** 당기순이익 (원) */
    private long netIncome;

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
     * 프롬프트에 포함할 재무 요약 텍스트를 반환합니다.
     */
    public String toPromptContext() {
        return String.format("""
                [%s (%s) %d사업연도 재무 요약]
                매출액: %,d원
                전기 매출액: %,d원  (전년 대비 증감률: %s)
                영업이익: %,d원  (영업이익률: %s)
                당기순이익: %,d원  (순이익률: %s)
                자산총계: %,d원
                부채총계: %,d원
                자본총계: %,d원
                부채비율: %s
                ROE: %s
                """,
                corpName, corpCode, businessYear,
                revenue,
                revenuePrev, fmtRate(revenueGrowth),
                operatingIncome, fmtRate(operatingMargin),
                netIncome, fmtRate(netMargin),
                totalAssets,
                totalLiabilities,
                totalEquity,
                fmtRate(debtRatio),
                fmtRate(roe)
        );
    }

    private static String fmtRate(Double rate) {
        return rate == null ? "산출 불가" : String.format("%.2f%%", rate);
    }
}
