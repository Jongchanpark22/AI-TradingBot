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
 * MA 풀백 (EMA20 눌림목) 전략.
 *
 * <p>Donchian Breakout과 정반대 타이밍 — 돌파가 아닌 눌림목에 진입하여
 * 학습 데이터 다양성을 높인다.
 *
 * <h3>진입 조건 (모두 만족)</h3>
 * <ol>
 *   <li>상승 추세 확인: 종가 &gt; SMA50 (중기 상승추세)</li>
 *   <li>눌림목 확인: 저가 ≤ EMA20 × 1.005 (EMA20에 근접 혹은 터치)</li>
 *   <li>반등 확인: 종가 &gt; 시가 (양봉)</li>
 *   <li>거래량 증가: 현재 봉 거래량 &gt; 직전 봉 거래량</li>
 * </ol>
 */
@Component
public final class MaPullbackStrategy implements Strategy {

    private final int sma50Period;
    private final int ema20Period;
    private final int atrPeriod;
    /** EMA20 터치 판정 허용 오차 (0.5%) */
    private static final double EMA_TOUCH_TOLERANCE = 1.005;

    public MaPullbackStrategy() {
        this(50, 20, 14);
    }

    public MaPullbackStrategy(int sma50Period, int ema20Period, int atrPeriod) {
        this.sma50Period = sma50Period;
        this.ema20Period = ema20Period;
        this.atrPeriod = atrPeriod;
    }

    @Override
    public String id() {
        return "MaPullback(EMA" + ema20Period + "/SMA" + sma50Period + ")";
    }

    @Override
    public StrategyType type() {
        return StrategyType.TREND_FOLLOWING;
    }

    @Override
    public Optional<StrategySignal> evaluate(List<Candle> candles) {
        int needed = Math.max(sma50Period + 1, Math.max(ema20Period + 1, atrPeriod * 2 + 1));
        if (candles == null || candles.size() < needed) return Optional.empty();

        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        double close  = dbl(last.getClosePrice());
        double open   = dbl(last.getOpenPrice());
        double low    = dbl(last.getLowPrice());
        double curVol = last.getVolume() != null ? last.getVolume().doubleValue() : 0.0;
        double prvVol = prev.getVolume() != null ? prev.getVolume().doubleValue() : 0.0;

        List<Double> closes = Indicators.closes(candles);

        // 1) 상승 추세: 종가 > SMA50
        double sma50 = Indicators.sma(closes, sma50Period);
        if (Double.isNaN(sma50) || close <= sma50) return Optional.empty();

        // 2) 눌림목: 저가가 EMA20 ± 허용 오차 이내
        double ema20 = Indicators.ema(closes, ema20Period);
        if (Double.isNaN(ema20) || low > ema20 * EMA_TOUCH_TOLERANCE) return Optional.empty();

        // 3) 반등 양봉
        if (close <= open) return Optional.empty();

        // 4) 거래량 증가
        if (curVol <= prvVol || prvVol <= 0) return Optional.empty();

        double atr = Indicators.atr(candles, atrPeriod);
        return Optional.of(new StrategySignal(StrategySignal.Direction.LONG, close, atr, id(), type()));
    }

    private static double dbl(java.math.BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }
}
