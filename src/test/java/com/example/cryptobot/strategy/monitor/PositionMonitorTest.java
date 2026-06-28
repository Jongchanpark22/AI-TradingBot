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
            0.01, 1.5, 2.0, 1.0, 3.0, 0.05, 3));

    private MonitoredPosition samplePosition() {
        // entry=100, ATR=2, stop=100-3=97, TP=100+6=106
        return new MonitoredPosition(
                1L, "KRW-BTC", 100.0,
                97.0,    // initialStop
                97.0,    // currentStop
                106.0,   // takeProfit
                100.0,   // highestSeen
                2.0,     // atr
                false,   // partialDone
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
                price, mp.highestSeen, mp.atr, mp.partialDone);

        assertTrue(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
    }

    @Test
    void priceAtStopTriggersExit() {
        MonitoredPosition mp = samplePosition();
        double price = 97.0; // exactly at stop

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone);

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
                price, mp.highestSeen, mp.atr, mp.partialDone);

        assertFalse(d.shouldExitNow());
        assertTrue(d.shouldPartialExit());
        // stop should move to break-even (entry price)
        assertEquals(100.0, d.newStopLoss(), 0.001);
    }

    @Test
    void noPartialExitAfterAlreadyDone() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 100.0; // already at break-even
        double price = 104.0;
        mp.highestSeen = price;

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone);

        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
    }

    @Test
    void trailingStopRatchetsUp() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 100.0; // at break-even after partial
        mp.highestSeen = 110.0;
        double price = 108.0;

        // chandelier = 110 - 3.0 * 2.0 = 104
        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone);

        assertFalse(d.shouldExitNow());
        assertEquals(104.0, d.newStopLoss(), 0.001);
    }

    @Test
    void trailingStopNeverMovesDown() {
        MonitoredPosition mp = samplePosition();
        mp.partialDone = true;
        mp.currentStop = 105.0; // already trailed high
        mp.highestSeen = 110.0;
        double price = 108.0;

        // chandelier = 110 - 6 = 104, but current is 105 → stays at 105
        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone);

        assertEquals(105.0, d.newStopLoss(), 0.001);
    }

    @Test
    void holdWhenPriceInNeutralZone() {
        MonitoredPosition mp = samplePosition();
        double price = 100.5; // slightly above entry, below +1R

        TrailingDecision d = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                price, mp.highestSeen, mp.atr, mp.partialDone);

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
        assertEquals(0, BigDecimal.valueOf(0.5).compareTo(mp.quantity));
    }

    @Test
    void highestSeenUpdatesOnNewHigh() {
        MonitoredPosition mp = samplePosition();
        // Simulate tick processing — highest seen updates
        double newPrice = 105.0;
        if (newPrice > mp.highestSeen) {
            mp.highestSeen = newPrice;
        }
        assertEquals(105.0, mp.highestSeen);

        // lower price doesn't change it
        double lowerPrice = 103.0;
        if (lowerPrice > mp.highestSeen) {
            mp.highestSeen = lowerPrice;
        }
        assertEquals(105.0, mp.highestSeen); // unchanged
    }

    @Test
    void fullLifecycle_entryToTrailingStop() {
        MonitoredPosition mp = samplePosition();
        // entry=100, stop=97, atr=2

        // price rises to 103 → partial exit at +1R
        mp.highestSeen = 103.5;
        TrailingDecision d1 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                103.5, mp.highestSeen, mp.atr, mp.partialDone);
        assertTrue(d1.shouldPartialExit());
        mp.partialDone = true;
        mp.currentStop = d1.newStopLoss(); // 100 (break-even)

        // price rises to 112 → trailing ratchets
        mp.highestSeen = 112.0;
        TrailingDecision d2 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                110.0, mp.highestSeen, mp.atr, mp.partialDone);
        // chandelier = 112 - 6 = 106
        assertEquals(106.0, d2.newStopLoss(), 0.001);
        mp.currentStop = d2.newStopLoss();

        // price drops to 105 → below trailing stop of 106 → exit
        TrailingDecision d3 = risk.updateTrailing(
                mp.entryPrice, mp.initialStop, mp.currentStop,
                105.0, mp.highestSeen, mp.atr, mp.partialDone);
        assertTrue(d3.shouldExitNow());
    }
}
