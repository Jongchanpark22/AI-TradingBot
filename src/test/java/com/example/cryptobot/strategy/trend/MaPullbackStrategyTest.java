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
 * MaPullbackStrategy 단위 테스트.
 *
 * <p>레짐 분류기를 우회하고, 4개 필터(SMA50 위, EMA20 터치, 양봉, 거래량 증가)만 직접 검증한다.
 */
class MaPullbackStrategyTest {

    private final MaPullbackStrategy strategy = new MaPullbackStrategy();

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
     * 상승 추세에서 EMA20 눌림목 발생 후 양봉 + 거래량 증가 → 신호 발생.
     *
     * <p>55봉 모두 100.0에서 출발(SMA50≈100, EMA20≈100).
     * 마지막 봉: low=100.0(EMA20 터치), close=100.5 > open=100.2(양봉), vol=2000 > prev vol=1000.
     */
    private static List<Candle> uptrendWithPullback() {
        List<Candle> out = new ArrayList<>();
        // 53봉: 서서히 상승 (EMA20/SMA50 모두 100 부근)
        for (int i = 0; i < 53; i++) {
            out.add(bar(100.0, 100.5, 99.8, 100.1, 1000));
        }
        // 직전 봉: 일반 봉 (vol=1000)
        out.add(bar(100.1, 100.6, 99.9, 100.2, 1000));
        // 눌림목 양봉: low=EMA20 터치, close > open, vol 증가
        out.add(bar(100.2, 100.9, 100.0, 100.5, 2000));
        return out;
    }

    @Test
    void firesOnUptrendPullback() {
        Optional<StrategySignal> sig = strategy.evaluate(uptrendWithPullback());
        assertTrue(sig.isPresent(), "MA 눌림목 조건 충족 시 신호 발생해야 함");
        assertEquals(StrategySignal.Direction.LONG, sig.get().direction());
        assertEquals(StrategyType.TREND_FOLLOWING, sig.get().strategyType());
        assertTrue(sig.get().atr() > 0);
        assertTrue(sig.get().entryPrice() > 0);
    }

    @Test
    void doesNotFireWhenLastBarIsRed() {
        List<Candle> c = uptrendWithPullback();
        // 마지막 봉을 음봉으로 교체 (open > close)
        c.set(c.size() - 1, bar(100.8, 101.0, 100.0, 100.2, 2000));
        assertTrue(strategy.evaluate(c).isEmpty(), "음봉에서는 신호 없어야 함");
    }

    @Test
    void doesNotFireWithoutVolumeIncrease() {
        List<Candle> c = uptrendWithPullback();
        // 마지막 봉 거래량을 직전(1000)보다 낮게 설정
        c.set(c.size() - 1, bar(100.2, 100.9, 100.0, 100.5, 800));
        assertTrue(strategy.evaluate(c).isEmpty(), "거래량 감소 시 신호 없어야 함");
    }

    @Test
    void doesNotFireWhenCloseIsBelowSma50() {
        List<Candle> c = new ArrayList<>();
        // 60봉 하락 추세 → 마지막 봉 close < SMA50
        for (int i = 0; i < 55; i++) {
            double p = 200.0 - i;
            c.add(bar(p, p + 0.5, p - 0.5, p - 0.1, 1000));
        }
        // 마지막 봉: 양봉이지만 SMA50보다 훨씬 아래
        c.add(bar(145, 146, 144, 145.5, 2000));
        assertTrue(strategy.evaluate(c).isEmpty(), "SMA50 아래에서는 신호 없어야 함");
    }

    @Test
    void doesNotFireWithInsufficientHistory() {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < 20; i++) c.add(bar(100, 101, 99, 100, 1000));
        assertTrue(strategy.evaluate(c).isEmpty(), "캔들 부족 시 신호 없어야 함");
    }

    @Test
    void strategyIdAndTypeAreCorrect() {
        assertEquals("MaPullback(EMA20/SMA50)", strategy.id());
        assertEquals(StrategyType.TREND_FOLLOWING, strategy.type());
    }
}
