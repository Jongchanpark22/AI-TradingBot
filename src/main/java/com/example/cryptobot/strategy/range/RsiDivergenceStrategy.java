package com.example.cryptobot.strategy.range;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategySignal;
import com.example.cryptobot.strategy.core.StrategyType;
import com.example.cryptobot.strategy.indicator.Indicators;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RSI 상승 다이버전스 전략 (반전형).
 *
 * <p>가격은 저점을 낮추는데(lower low) RSI는 저점을 높이는(higher low) 다이버전스는
 * 매도 모멘텀 소진의 고신뢰 반전 신호다. MeanReversion/Donchian과 완전히 다른 성격이라
 * 멀티전략 학습 데이터 다양성에 크게 기여한다.
 *
 * <h3>진입 조건</h3>
 * <ol>
 *   <li>최근 스윙 저점(가격): 직전 스윙 저점보다 낮음 (lower low)</li>
 *   <li>최근 스윙 저점(RSI): 직전 스윙 저점의 RSI보다 높음 (higher low)</li>
 *   <li>다이버전스 확인 봉: 양봉 (종가 &gt; 시가)</li>
 * </ol>
 */
@Component
public final class RsiDivergenceStrategy implements Strategy {

    private final int rsiPeriod;
    private final int atrPeriod;
    /** 스윙 저점 탐색 범위: 최근 N봉 이내 */
    private final int lookbackBars;
    /** 스윙 저점 판정 양쪽 반경 */
    private final int swingHalf;

    public RsiDivergenceStrategy() {
        this(14, 14, 60, 3);
    }

    public RsiDivergenceStrategy(int rsiPeriod, int atrPeriod, int lookbackBars, int swingHalf) {
        this.rsiPeriod   = rsiPeriod;
        this.atrPeriod   = atrPeriod;
        this.lookbackBars = lookbackBars;
        this.swingHalf   = swingHalf;
    }

    @Override
    public String id() {
        return "RsiDivergence(RSI" + rsiPeriod + ")";
    }

    @Override
    public StrategyType type() {
        return StrategyType.MEAN_REVERSION;
    }

    @Override
    public Optional<StrategySignal> evaluate(List<Candle> candles) {
        int needed = Math.max(lookbackBars + swingHalf + 2,
                Math.max(rsiPeriod + 2, atrPeriod * 2 + 1));
        if (candles == null || candles.size() < needed) return Optional.empty();

        Candle last = candles.get(candles.size() - 1);
        double close = dbl(last.getClosePrice());
        double open  = dbl(last.getOpenPrice());

        // 3) 현재 봉 양봉 확인 (먼저 체크해서 계산량 절약)
        if (close <= open) return Optional.empty();

        // 스윙 저점 탐색 (현재 봉 제외)
        List<Integer> swingLows = findSwingLows(candles, candles.size() - 1);
        if (swingLows.size() < 2) return Optional.empty();

        // 가장 최근 두 스윙 저점
        int idx2 = swingLows.get(swingLows.size() - 1); // 더 최근 (낮아야 함)
        int idx1 = swingLows.get(swingLows.size() - 2); // 더 이전

        double low1 = dbl(candles.get(idx1).getLowPrice());
        double low2 = dbl(candles.get(idx2).getLowPrice());

        // 1) 가격 lower low
        if (low2 >= low1) return Optional.empty();

        // 각 스윙 저점에서의 RSI 계산 (닫힌 봉까지의 RSI)
        List<Double> closes = Indicators.closes(candles);
        double rsi1 = Indicators.rsi(closes.subList(0, idx1 + 1), rsiPeriod);
        double rsi2 = Indicators.rsi(closes.subList(0, idx2 + 1), rsiPeriod);

        // 2) RSI higher low
        if (rsi2 <= rsi1) return Optional.empty();

        double atr = Indicators.atr(candles, atrPeriod);
        return Optional.of(new StrategySignal(StrategySignal.Direction.LONG, close, atr, id(), type()));
    }

    /**
     * 윈도우 내 스윙 저점 인덱스 목록 반환 (오름차순).
     *
     * @param candles   캔들 리스트
     * @param endExcl   탐색 종료 인덱스 (exclusive, 보통 현재 봉 인덱스)
     */
    private List<Integer> findSwingLows(List<Candle> candles, int endExcl) {
        List<Integer> result = new ArrayList<>();
        int start = Math.max(swingHalf, endExcl - lookbackBars);
        int end   = endExcl - swingHalf - 1; // 확인 봉(swingHalf개) 이전까지만

        for (int i = start; i <= end; i++) {
            double low = dbl(candles.get(i).getLowPrice());
            boolean isMin = true;
            for (int j = i - swingHalf; j <= i + swingHalf; j++) {
                if (j == i || j < 0 || j >= candles.size()) continue;
                if (dbl(candles.get(j).getLowPrice()) <= low) {
                    isMin = false;
                    break;
                }
            }
            if (isMin) result.add(i);
        }
        return result;
    }

    private static double dbl(java.math.BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }
}
