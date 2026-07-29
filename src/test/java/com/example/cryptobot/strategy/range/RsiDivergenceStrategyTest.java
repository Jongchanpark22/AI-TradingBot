package com.example.cryptobot.strategy.range;

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
 * RsiDivergenceStrategy 단위 테스트.
 *
 * <p>핵심 불변식: 가격 lower-low + RSI higher-low(불리시 다이버전스) + 양봉 현재봉.
 */
class RsiDivergenceStrategyTest {

    private final RsiDivergenceStrategy strategy = new RsiDivergenceStrategy();

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
     * RSI 불리시 다이버전스 시나리오.
     *
     * <p>Phase 1(0~19): 중립 구간 (가격 100, RSI ≈ 50)
     * Phase 2(20~29): 급락 100→80 — 첫 번째 스윙 저점 (bar 29, RSI 매우 낮음)
     * Phase 3(30~44): 반등 80→90
     * Phase 4(45~54): 완만한 재하락 90→77 — 두 번째 스윙 저점 (bar 54, 가격↓ RSI↑)
     * Phase 5(55~64): 79 수준 횡보 — 스윙 저점 확정 완충 구간
     * Phase 6(65): 양봉 반전 확인봉
     *
     * <p>findSwingLows 탐색 범위: [max(3,65-60)=5 , 65-3-1=61]. bar 54는 범위 내에 있다.
     */
    private static List<Candle> bullishDivergence() {
        List<Candle> out = new ArrayList<>();

        // Phase 1: 중립
        for (int i = 0; i < 20; i++) {
            out.add(bar(100, 101, 99, 100, 1000));
        }
        // Phase 2: 급락 (10봉, 100→80) — bar 29가 첫 스윙 저점
        for (int i = 0; i < 10; i++) {
            double p = 100.0 - (i + 1) * 2.0;
            out.add(bar(p + 0.5, p + 1, p - 0.3, p, 1500));
        }
        // Phase 3: 반등 (15봉, 80→90)
        for (int i = 0; i < 15; i++) {
            double p = 80.0 + (i + 1) * 0.67;
            out.add(bar(p - 0.5, p + 0.5, p - 1, p, 1000));
        }
        // Phase 4: 완만한 하락 (10봉, 90→77) — bar 54가 두 번째 스윙 저점
        for (int i = 0; i < 10; i++) {
            double p = 90.0 - (i + 1) * 1.3;
            out.add(bar(p + 0.3, p + 0.8, p - 0.2, p, 1100));
        }
        // Phase 5: 79 수준 횡보 (10봉) — 스윙 저점(bar 54) 오른쪽 완충
        for (int i = 0; i < 10; i++) {
            out.add(bar(78.5, 79.5, 78.0, 79.0, 1000));
        }
        // Phase 6: 현재봉 양봉 반전 (bar 65)
        out.add(bar(79.0, 80.5, 78.5, 80.2, 1300));
        return out;
    }

    @Test
    void firesOnBullishDivergence() {
        Optional<StrategySignal> sig = strategy.evaluate(bullishDivergence());
        assertTrue(sig.isPresent(), "불리시 다이버전스 조건 충족 시 신호 발생해야 함");
        assertEquals(StrategySignal.Direction.LONG, sig.get().direction());
        assertEquals(StrategyType.MEAN_REVERSION, sig.get().strategyType());
        assertTrue(sig.get().atr() > 0);
    }

    @Test
    void doesNotFireWhenLastBarIsRed() {
        List<Candle> c = bullishDivergence();
        // 마지막 봉 음봉으로 변경
        c.set(c.size() - 1, bar(69.5, 70.0, 67.5, 68.0, 1200));
        assertTrue(strategy.evaluate(c).isEmpty(), "음봉에서는 신호 없어야 함");
    }

    @Test
    void doesNotFireWhenNoDivergence() {
        List<Candle> c = new ArrayList<>();
        // 단순 하락 추세 — 가격 lower-low + RSI lower-low (다이버전스 없음)
        for (int i = 0; i < 30; i++) {
            double p = 200.0 - i * 2;
            c.add(bar(p, p + 0.5, p - 0.5, p - 0.3, 1000));
        }
        // 반등 없이 계속 하락
        for (int i = 0; i < 30; i++) {
            double p = 140.0 - i * 2;
            c.add(bar(p, p + 0.5, p - 0.5, p - 0.3, 1100));
        }
        // 양봉이지만 다이버전스 없음
        c.add(bar(79.0, 80.5, 78.5, 80.0, 1200));
        assertTrue(strategy.evaluate(c).isEmpty(), "다이버전스 없이는 신호 없어야 함");
    }

    @Test
    void doesNotFireWithInsufficientHistory() {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < 30; i++) c.add(bar(100, 101, 99, 100, 1000));
        assertTrue(strategy.evaluate(c).isEmpty(), "캔들 부족 시 신호 없어야 함");
    }

    @Test
    void strategyIdAndTypeAreCorrect() {
        assertEquals("RsiDivergence(RSI14)", strategy.id());
        assertEquals(StrategyType.MEAN_REVERSION, strategy.type());
    }
}
