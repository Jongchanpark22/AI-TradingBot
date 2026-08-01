package com.example.cryptobot.report.dto;

/**
 * DART 재무제표 API 단일 계정 항목.
 *
 * @param sjDiv           재무제표 구분 (BS: 재무상태표, IS: 손익계산서, CIS: 포괄손익)
 * @param accountNm       계정명 (예: 매출액, 영업이익, 자산총계)
 * @param thstrmAmount    당기 금액 (원, 콤마 포함 문자열)
 * @param frmtrmAmount    전기 금액
 * @param bfefrmtrmAmount 전전기 금액
 */
public record FinancialAccount(
        String sjDiv,
        String accountNm,
        String thstrmAmount,
        String frmtrmAmount,
        String bfefrmtrmAmount
) {

    /**
     * 당기 금액을 long으로 변환합니다. 파싱 실패 시 0 반환.
     */
    public long thstrmLong() {
        return parseAmount(thstrmAmount);
    }

    /**
     * 전기 금액을 long으로 변환합니다. 파싱 실패 시 0 반환.
     */
    public long frmtrmLong() {
        return parseAmount(frmtrmAmount);
    }

    private static long parseAmount(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) return 0L;
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
