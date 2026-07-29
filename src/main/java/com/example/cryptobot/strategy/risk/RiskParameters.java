package com.example.cryptobot.strategy.risk;

/**
 * ATR 기반 리스크·청산 파라미터.
 *
 * <ul>
 *   <li>{@code riskPerTrade = 0.01} — 거래당 리스크 비율 (자본의 1%).</li>
 *   <li>{@code stopAtrMultiplier = 3.0} — 초기 손절 = 진입가 − 3·ATR.</li>
 *   <li>{@code takeProfitRMultiple = 3.0} — 하드 익절 상한 = stopDist × 3.0 (0이면 트레일링에 위임).</li>
 *   <li>{@code partialExitRMultiple = 1.0} — +1R 달성 시 1차 부분청산 트리거.</li>
 *   <li>{@code partialExitRatio = 0.5} — 1차 부분청산 비율 (0.5 = 원포지션 50%).</li>
 *   <li>{@code partial2RMultiple = 2.0} — +2R 달성 시 2차 부분청산 트리거.</li>
 *   <li>{@code partial2Ratio = 0.5} — 2차 부분청산 비율 (0.5 = 남은 포지션 50% = 원포지션 25%).</li>
 *   <li>{@code trailingAtrMultiplier = 3.0} — 기본 챈들리어 = 최고가 − 3·ATR.</li>
 *   <li>{@code trailAtrMultAfterP2 = 1.5} — +2R 도달 후 강화 트레일링 = 최고가 − 1.5·ATR.</li>
 *   <li>{@code maxDailyLoss = 0.05} — 일별 손실 킬스위치 (-5%).</li>
 *   <li>{@code maxOpenPositions = 3}</li>
 * </ul>
 */
public record RiskParameters(
        double riskPerTrade,
        double stopAtrMultiplier,
        double takeProfitRMultiple,
        double partialExitRMultiple,
        double partialExitRatio,
        double partial2RMultiple,
        double partial2Ratio,
        double trailingAtrMultiplier,
        double trailAtrMultAfterP2,
        double maxDailyLoss,
        int maxOpenPositions
) {
    /**
     * 기본값: SL 3×ATR, 1차 +1R 50%, 2차 +2R 50%(잔여분), 기본트레일 3×ATR, 강화트레일 1.5×ATR, 하드TP +3R.
     */
    public static RiskParameters defaults() {
        return new RiskParameters(0.01, 3.0, 3.0, 1.0, 0.5, 2.0, 0.5, 3.0, 1.5, 0.05, 3);
    }

    /** 입력 검증. 잘못된 값은 {@link IllegalArgumentException}을 던진다. */
    public RiskParameters {
        if (riskPerTrade <= 0 || riskPerTrade > 0.1)
            throw new IllegalArgumentException("riskPerTrade must be in (0, 0.1], was " + riskPerTrade);
        if (stopAtrMultiplier <= 0)
            throw new IllegalArgumentException("stopAtrMultiplier must be > 0");
        if (takeProfitRMultiple < 0)
            throw new IllegalArgumentException("takeProfitRMultiple must be >= 0");
        if (partialExitRMultiple < 0)
            throw new IllegalArgumentException("partialExitRMultiple must be >= 0");
        if (partialExitRatio < 0 || partialExitRatio > 1)
            throw new IllegalArgumentException("partialExitRatio must be in [0, 1], was " + partialExitRatio);
        if (partial2RMultiple < 0)
            throw new IllegalArgumentException("partial2RMultiple must be >= 0");
        if (partial2Ratio < 0 || partial2Ratio > 1)
            throw new IllegalArgumentException("partial2Ratio must be in [0, 1], was " + partial2Ratio);
        if (trailingAtrMultiplier <= 0)
            throw new IllegalArgumentException("trailingAtrMultiplier must be > 0");
        if (trailAtrMultAfterP2 <= 0)
            throw new IllegalArgumentException("trailAtrMultAfterP2 must be > 0");
        if (maxDailyLoss <= 0 || maxDailyLoss > 0.5)
            throw new IllegalArgumentException("maxDailyLoss must be in (0, 0.5]");
        if (maxOpenPositions <= 0)
            throw new IllegalArgumentException("maxOpenPositions must be > 0");
    }
}
