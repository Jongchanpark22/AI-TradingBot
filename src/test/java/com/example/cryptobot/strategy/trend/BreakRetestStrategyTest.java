package com.example.cryptobot.strategy.trend;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.StrategySignal;
import com.example.cryptobot.strategy.core.StrategyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BreakRetestStrategy 단위 테스트.
 *
 * <p>핵심 불변식: 20봉 고가 돌파(거래량 2×) → 현재봉이 돌파 레벨 ±0.5% 이내 → RSI 40~60 → 양봉.
 */
class BreakRetestStrategyTest {

    private final BreakRetestStrategy strategy = new BreakRetestStrategy();

    private static Candle bar(double o, double h, double l, double c, double vol) {
        return Candle.builder()
                .symbol("TEST").period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(LocalDateTime.now())
                .openPrice(BigDecimal.valueOf(o))
                .highPrice(BigDecimal.valueOf(h))
                .lowPrice(BigDecimal.valueOf(l))
                .closePrice(BigDecimal.valueOf(c))
                .volume(BigDecimal.valueOf(vol))
                .quoteAssetVolume(BigDecimal.valueOf(vol * c))
                .build();
    }

    /**
     * 정석 Break & Retest 시나리오.
     *
     * <p>needed = 20+15+10+14+2 = 61.
     *
     * <p>0~49봉: 교번봉 (close 100.0↔100.5) — avg_gain≈avg_loss → RSI≈50 기반 확립.
     *   high≈100.8 → Donchian20 prior high≈100.8
     * 50봉: 돌파! close=102 > 100.8, vol=3000 (prior 10봉 avg=1000, 3배)
     * 51~59봉: 서서히 하락 (102→101.1, -0.1/봉) — 9봉 손실로 RSI 하강
     * 마지막봉(60): 리테스트 양봉 close=101.6.
     *   |101.6-102|/102≈0.39%<0.5% ✓. RSI≈56 [40~60] ✓
     */
    private static List<Candle> breakAndRetest() {
        List<Candle> out = new ArrayList<>();

        // 교번봉 50봉: RSI 기반 ≈50 확립
        for (int i = 0; i < 50; i++) {
            boolean up = (i % 2 == 0);
            double c = up ? 100.5 : 100.0;
            double o = up ? 100.0 : 100.5;
            out.add(bar(o, c + 0.3, o - 0.3, c, 1000));
        }
        // 돌파봉 (index 50): close=102 > Donchian≈100.8, vol=3000
        out.add(bar(100.8, 102.5, 100.5, 102.0, 3000));

        // 되돌림 (9봉, 102→101.1, -0.1/봉)
        for (int i = 0; i < 9; i++) {
            double p = 102.0 - (i + 1) * 0.1;
            out.add(bar(p + 0.05, p + 0.2, p - 0.1, p, 1000));
        }
        // 리테스트 양봉 (index 60): close=101.6, RSI≈56
        out.add(bar(101.3, 101.9, 101.1, 101.6, 1200));
        return out;
    }

    @Test
    void firesOnBreakAndRetest() {
        Optional<StrategySignal> sig = strategy.evaluate(breakAndRetest());
        assertTrue(sig.isPresent(), "Break & Retest 조건 충족 시 신호 발생해야 함");
        assertEquals(StrategySignal.Direction.LONG, sig.get().direction());
        assertEquals(StrategyType.BREAKOUT, sig.get().strategyType());
        assertTrue(sig.get().atr() > 0);
        assertTrue(sig.get().entryPrice() > 0);
    }

    @Test
    void doesNotFireWhenLastBarIsRed() {
        List<Candle> c = breakAndRetest();
        // 리테스트 봉을 음봉으로 교체
        c.set(c.size() - 1, bar(106.2, 106.5, 105.0, 105.3, 1200));
        assertTrue(strategy.evaluate(c).isEmpty(), "음봉에서는 신호 없어야 함");
    }

    @Test
    void doesNotFireWhenPriceTooFarFromBreakout() {
        List<Candle> c = breakAndRetest();
        // 리테스트 봉 종가를 돌파 레벨(106)에서 2% 이상 이탈시킴
        c.set(c.size() - 1, bar(103.0, 104.0, 102.5, 103.5, 1200));
        assertTrue(strategy.evaluate(c).isEmpty(), "돌파 레벨 ±0.5% 초과 시 신호 없어야 함");
    }

    @Test
    void doesNotFireWithNoBreakout() {
        List<Candle> c = new ArrayList<>();
        // 단순 횡보만 — 돌파 봉 없음
        for (int i = 0; i < 55; i++) {
            c.add(bar(99.5, 100.2, 99.0, 100.0, 1000));
        }
        // 현재봉 양봉
        c.add(bar(99.8, 100.5, 99.5, 100.2, 1200));
        assertTrue(strategy.evaluate(c).isEmpty(), "돌파 없이는 신호 없어야 함");
    }

    @Test
    void doesNotFireWithInsufficientHistory() {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < 20; i++) c.add(bar(100, 101, 99, 100, 1000));
        assertTrue(strategy.evaluate(c).isEmpty(), "캔들 부족 시 신호 없어야 함");
    }

    @Test
    void strategyIdAndTypeAreCorrect() {
        assertEquals("BreakRetest(D20/LB15)", strategy.id());
        assertEquals(StrategyType.BREAKOUT, strategy.type());
    }
}
