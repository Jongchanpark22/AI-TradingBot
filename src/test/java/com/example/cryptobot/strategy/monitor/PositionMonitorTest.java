package com.example.cryptobot.strategy.monitor;

import com.example.cryptobot.strategy.monitor.PositionMonitor.MonitoredPosition;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.risk.TrailingDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PositionMonitor's core logic. These tests verify the
 * MonitoredPosition state transitions using the RiskManager directly,
 * without requiring Spring context or database.
 */
class PositionMonitorTest {

    private final RiskManager risk = new RiskManager(new RiskParameters(
            0.01, 1.5, 2.0, 1.0, 0.5, 2.0, 0.5, 3.0, 1.5, 0.05, 3));

    private MonitoredPosition samplePosition() {
        // entry=100, ATR=2, stop=97, TP=106, initRisk=3
        // 1차 트리거: 100+3×1.0=103, 2차 트리거: 100+3×2.0=106
        return new MonitoredPosition(
                1L, "KRW-BTC", 100.0,
                97.0,    // initialStop
                97.0,    // currentStop
                106.0,   // takeProfit
                100.0,   // highestSeen
                2.0,     // atr
                false,   // partialDone
                false,   // secondPartialDone
                BigDecimal.valueOf(0.5),
                false,   // aboveProfitTarget
                null     // signalId
        );
    }

    @Test
    void stopLossHitTriggersFullExit() {
        MonitoredPosition mp = samplePosition();
        double price = 96.5; // below stop of 97

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertTrue(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
    }

    @Test
    void priceAtStopTriggersExit() {
        MonitoredPosition mp = samplePosition();
        double price = 97.0; // exactly at stop

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertTrue(d.shouldExitNow());
    }

    @Test
    void partialExitAt1R() {
        MonitoredPosition mp = samplePosition();
        // initialRisk = 100 - 97 = 3, so +1R = 103
        double price = 103.5;
        mp.highestSeen = price;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertTrue(d.shouldPartialExit());
        assertFalse(d.shouldSecondPartialExit());
        // stop should move to break-even (entry price)
        assertEquals(100.0, d.newStopLoss(), 0.001);
    }

    @Test
    void secondPartialExitAt2R() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;         // 1차 완료
        mp.currentStop = 100.0;        // 손절 본전으로 이동된 상태
        // +2R = 100 + 3×2.0 = 106
        double price = 106.5;
        mp.highestSeen = price;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
        assertTrue(d.shouldSecondPartialExit());
    }

    @Test
    void noPartialExitAfterAlreadyDone() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 100.0; // already at break-even
        // price=104 < 2차 트리거(106) → 2차 청산도 발생 안 함
        double price = 104.0;
        mp.highestSeen = price;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
        assertFalse(d.shouldSecondPartialExit());
    }

    @Test
    void trailingStopRatchetsUp() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 100.0; // at break-even after partial
        mp.highestSeen = 110.0;
        // price=104 < 2차 트리거(106) → 기본 트레일링(3.0×ATR=6) 적용
        // chandelier = 110 - 6 = 104
        double price = 104.0;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertEquals(104.0, d.newStopLoss(), 0.001);
    }

    @Test
    void trailingStopTightensAfterSecondPartial() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.secondPartialDone = true;   // 2차 완료 → 강화 트레일링(1.5×ATR=3) 적용
        mp.currentStop = 100.0;
        mp.highestSeen = 110.0;
        // chandelier = 110 - 3 = 107
        double price = 108.0;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertEquals(107.0, d.newStopLoss(), 0.001);
    }

    @Test
    void trailingStopNeverMovesDown() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 105.0; // already trailed high
        mp.highestSeen = 110.0;
        // price=103 < 2차 트리거(106) → 기본 트레일링(3.0×ATR=6) → chandelier=104 < 105 → 유지
        double price = 103.0;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertEquals(105.0, d.newStopLoss(), 0.001);
    }

    @Test
    void holdWhenPriceInNeutralZone() {
        MonitoredPosition mp = samplePosition();
        double price = 100.5; // slightly above entry, below +1R

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);

        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
        assertEquals(97.0, d.newStopLoss(), 0.001); // no change
    }

    @Test
    void monitoredPositionFromValues() {
        MonitoredPosition mp = samplePosition();
        assertEquals("KRW-BTC", mp.symbol);
        assertEquals(100.0, mp.entryPrice);
        assertEquals(97.0, mp.currentStop);
        assertFalse(mp.partialDone);
        assertFalse(mp.secondPartialDone);
        assertEquals(0, BigDecimal.valueOf(0.5).compareTo(mp.quantity));
    }

    @Test
    void highestSeenUpdatesOnNewHigh() {
        MonitoredPosition mp = samplePosition();
        double newPrice = 105.0;
        if (newPrice > mp.highestSeen) {
            mp.highestSeen = newPrice;
        }
        assertEquals(105.0, mp.highestSeen);

        double lowerPrice = 103.0;
        if (lowerPrice > mp.highestSeen) {
            mp.highestSeen = lowerPrice;
        }
        assertEquals(105.0, mp.highestSeen); // unchanged
    }

    @Test
    void fullLifecycle_twoPartialsThenTrailingExit() {
        MonitoredPosition mp = samplePosition();
        // entry=100, stop=97, atr=2, initRisk=3

        // 1. price 103.5 → 1차 청산 (+1R)
        mp.highestSeen = 103.5;
        TrailingDecision d1 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                103.5, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);
        assertTrue(d1.shouldPartialExit());
        mp.partialDone = true;
        mp.currentStop = d1.newStopLoss(); // 100.0 (break-even)

        // 2. price 106.5 → 2차 청산 (+2R)
        mp.highestSeen = 106.5;
        TrailingDecision d2 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                106.5, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);
        assertTrue(d2.shouldSecondPartialExit());
        mp.secondPartialDone = true;
        mp.currentStop = d2.newStopLoss();

        // 3. price 112 → 강화 트레일링 1.5×ATR=3 → chandelier=112-3=109
        mp.highestSeen = 112.0;
        TrailingDecision d3 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                112.0, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);
        assertEquals(109.0, d3.newStopLoss(), 0.001);
        mp.currentStop = d3.newStopLoss();

        // 4. price 108 < stop 109 → 청산
        TrailingDecision d4 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                108.0, mp.highestSeen, mp.atr, mp.partialDone, mp.secondPartialDone);
        assertTrue(d4.shouldExitNow());
    }
}
