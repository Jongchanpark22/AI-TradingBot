package com.example.cryptobot.strategy.trend;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategySignal;
import com.example.cryptobot.strategy.core.StrategyType;
import com.example.cryptobot.strategy.indicator.Indicators;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 돌파 후 재테스트(Break &amp; Retest) 전략.
 *
 * <p>Donchian Breakout의 정교화 버전. 단순 돌파 진입 대신 돌파 확인 후
 * 돌파 레벨로 되돌아온 시점에 진입하여 가짜 돌파(fakeout) 승률을 줄인다.
 *
 * <h3>진입 조건</h3>
 * <ol>
 *   <li>최근 {lookback}봉 이내에 20봉 고가 돌파 발생 + 그 봉 거래량 ≥ 직전 10봉 평균 × 2.0</li>
 *   <li>현재 봉 종가가 돌파 레벨 ±0.5% 이내 (리테스트 구간)</li>
 *   <li>현재 봉 RSI(14) 40~60 (추세 유지, 반전 아님)</li>
 *   <li>현재 봉 양봉 (종가 &gt; 시가)</li>
 * </ol>
 */
@Component
public final class BreakRetestStrategy implements Strategy {

    private final int donchianPeriod;
    private final int volumeAvgPeriod;
    private final double minVolumeMultiplier;
    private final int rsiPeriod;
    private final double rsiLow;
    private final double rsiHigh;
    private final int lookback;
    private final int atrPeriod;
    /** 리테스트 허용 오차 (±0.5%) */
    private static final double RETEST_TOLERANCE = 0.005;

    public BreakRetestStrategy() {
        this(20, 10, 2.0, 14, 40.0, 60.0, 15, 14);
    }

    public BreakRetestStrategy(int donchianPeriod, int volumeAvgPeriod, double minVolumeMultiplier,
                                int rsiPeriod, double rsiLow, double rsiHigh,
                                int lookback, int atrPeriod) {
        this.donchianPeriod      = donchianPeriod;
        this.volumeAvgPeriod     = volumeAvgPeriod;
        this.minVolumeMultiplier = minVolumeMultiplier;
        this.rsiPeriod           = rsiPeriod;
        this.rsiLow              = rsiLow;
        this.rsiHigh             = rsiHigh;
        this.lookback            = lookback;
        this.atrPeriod           = atrPeriod;
    }

    @Override
    public String id() {
        return "BreakRetest(D" + donchianPeriod + "/LB" + lookback + ")";
    }

    @Override
    public StrategyType type() {
        return StrategyType.BREAKOUT;
    }

    @Override
    public Optional<StrategySignal> evaluate(List<Candle> candles) {
        int needed = donchianPeriod + lookback + volumeAvgPeriod + rsiPeriod + 2;
        if (candles == null || candles.size() < needed) return Optional.empty();

        Candle last   = candles.get(candles.size() - 1);
        double close  = dbl(last.getClosePrice());
        double open   = dbl(last.getOpenPrice());

        // 4) 현재 봉 양봉 (먼저 체크)
        if (close <= open) return Optional.empty();

        // 3) 현재 봉 RSI 40~60
        List<Double> closes = Indicators.closes(candles);
        double rsi = Indicators.rsi(closes, rsiPeriod);
        if (rsi < rsiLow || rsi > rsiHigh) return Optional.empty();

        // 1) 최근 lookback봉 이내에서 유효한 돌파 바 탐색
        int breakoutIdx = findBreakoutBar(candles);
        if (breakoutIdx < 0) return Optional.empty();

        // 돌파 레벨 = 돌파 봉의 종가
        double breakoutLevel = dbl(candles.get(breakoutIdx).getClosePrice());
        if (breakoutLevel <= 0) return Optional.empty();

        // 2) 현재 봉이 돌파 레벨 ±0.5% 이내 (리테스트 구간)
        double distRatio = Math.abs(close - breakoutLevel) / breakoutLevel;
        if (distRatio > RETEST_TOLERANCE) return Optional.empty();

        double atr = Indicators.atr(candles, atrPeriod);
        return Optional.of(new StrategySignal(StrategySignal.Direction.LONG, close, atr, id(), type()));
    }

    /**
     * 현재 봉 직전 lookback봉 이내에서 가장 최근의 유효 돌파 바 인덱스를 반환한다.
     * 없으면 -1 반환.
     *
     * <p>유효 돌파 조건:
     * <ul>
     *   <li>종가 &gt; 직전 {donchianPeriod}봉 최고가</li>
     *   <li>거래량 ≥ 직전 {volumeAvgPeriod}봉 평균 × minVolumeMultiplier</li>
     * </ul>
     */
    private int findBreakoutBar(List<Candle> candles) {
        int n = candles.size();
        // n-1은 현재 봉 → n-2부터 역방향 탐색
        int scanStart = n - 2;
        int scanEnd   = Math.max(donchianPeriod + volumeAvgPeriod, n - 2 - lookback);

        for (int j = scanStart; j >= scanEnd; j--) {
            // 직전 donchianPeriod봉의 최고가 (j 이전)
            if (j - donchianPeriod < 0) break;
            double priorHigh = Double.NEGATIVE_INFINITY;
            for (int k = j - donchianPeriod; k < j; k++) {
                priorHigh = Math.max(priorHigh, dbl(candles.get(k).getHighPrice()));
            }

            double closeJ = dbl(candles.get(j).getClosePrice());
            if (closeJ <= priorHigh) continue;

            // 직전 volumeAvgPeriod봉 평균 거래량
            if (j - volumeAvgPeriod < 0) continue;
            double volSum = 0;
            for (int k = j - volumeAvgPeriod; k < j; k++) {
                volSum += candles.get(k).getVolume() != null
                        ? candles.get(k).getVolume().doubleValue() : 0.0;
            }
            double volAvg = volSum / volumeAvgPeriod;
            double volJ   = candles.get(j).getVolume() != null
                    ? candles.get(j).getVolume().doubleValue() : 0.0;

            if (volJ < volAvg * minVolumeMultiplier) continue;

            return j; // 유효한 돌파 바 발견
        }
        return -1;
    }

    private static double dbl(java.math.BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }
}
