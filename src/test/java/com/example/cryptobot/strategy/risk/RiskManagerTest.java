package com.example.cryptobot.strategy.risk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskManagerTest {

    private final RiskManager risk = new RiskManager(RiskParameters.defaults());

    // ============================================================
    // planLong
    // ============================================================

    @Test
    void planLongSizesQuantityFromRiskFractionAndAtr() {
        // equity 10,000, risk 1% -> $100 at risk
        // ATR 2.0, multiplier 1.5 -> stop distance 3.0
        // qty = 100 / 3 = 33.333...
        EntryPlan p = risk.planLong(10_000, 100.0, 2.0);
        assertTrue(p.isExecutable());
        assertEquals(100.0 - 3.0, p.stopLossPrice(), 1e-9);
        assertEquals(100.0 + 6.0, p.takeProfitPrice(), 1e-9); // 2R
        assertEquals(100.0 / 3.0, p.quantity(), 1e-9);
        assertEquals(100.0, p.riskAmount(), 1e-9);
        assertEquals(2.0, p.riskRewardRatio(), 1e-9); // 2R defaults
    }

    @Test
    void planLongShrinksWhenVolatilityIsHigh() {
        EntryPlan calm = risk.planLong(10_000, 100.0, 1.0);
        EntryPlan wild = risk.planLong(10_000, 100.0, 5.0);
        assertTrue(calm.quantity() > wild.quantity(),
                "higher ATR must yield smaller position size");
        // risk amount must be identical -> auto-adapts to vol
        assertEquals(calm.riskAmount(), wild.riskAmount(), 1e-9);
    }

    @Test
    void planLongRefusesNonsenseInputs() {
        assertFalse(risk.planLong(10_000, 100, 0).isExecutable());
        assertFalse(risk.planLong(10_000, 100, Double.NaN).isExecutable());
        assertFalse(risk.planLong(0, 100, 1).isExecutable());
        // ATR so wide it makes the stop negative
        assertFalse(risk.planLong(10_000, 1, 100).isExecutable());
    }

    // ============================================================
    // updateTrailing — stop hits / partial exit / chandelier
    // ============================================================

    @Test
    void trailingTriggersExitWhenPriceBreaksStop() {
        TrailingDecision d = risk.updateTrailing(
                100, 97, 97, 96.5, 100, 2.0, false);
        assertTrue(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
    }

    @Test
    void trailingPartialExitFiresAtOneRAndMovesStopToBreakEven() {
        // entry 100, stop 97 -> R=3 -> partial at 103
        TrailingDecision d = risk.updateTrailing(
                100, 97, 97, 103.0, 103.0, 2.0, false);
        assertFalse(d.shouldExitNow());
        assertTrue(d.shouldPartialExit());
        assertEquals(100.0, d.newStopLoss(), 1e-9, "stop should move to break-even");
    }

    @Test
    void chandelierTrailRatchetsStopUpButNeverDown() {
        // already in profit; highest seen 110, ATR 1, mul 3 -> chandelier 107
        TrailingDecision up = risk.updateTrailing(
                100, 97, 100, 109, 110, 1.0, true);
        assertEquals(107.0, up.newStopLoss(), 1e-9);

        // price pulls back; chandelier would move down -> stop must hold
        TrailingDecision pullback = risk.updateTrailing(
                100, 97, 107, 108, 110, 1.0, true);
        assertEquals(107.0, pullback.newStopLoss(), 1e-9);
    }

    @Test
    void trailingHoldsWhenNothingHappens() {
        TrailingDecision d = risk.updateTrailing(
                100, 97, 97, 100.5, 100.5, 2.0, false);
        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
        assertEquals(97.0, d.newStopLoss(), 1e-9);
    }

    // ============================================================
    // Equity guards
    // ============================================================

    @Test
    void dailyKillSwitchTripsAtConfiguredDrawdown() {
        // default maxDailyLoss = 5%
        assertFalse(risk.isDailyLossBreached(10_000, 9_700));   // -3%
        assertTrue(risk.isDailyLossBreached(10_000, 9_500));    // -5%
        assertTrue(risk.isDailyLossBreached(10_000, 9_000));    // -10%
    }

    @Test
    void positionSlotEnforcement() {
        assertTrue(risk.canOpenAnotherPosition(0));
        assertTrue(risk.canOpenAnotherPosition(2));
        assertFalse(risk.canOpenAnotherPosition(3));
        assertFalse(risk.canOpenAnotherPosition(99));
    }

    // ============================================================
    // RiskParameters validation
    // ============================================================

    @Test
    void riskParametersRejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0, 1.5, 2, 1, 3, 0.05, 3));
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.5, 1.5, 2, 1, 3, 0.05, 3)); // > 10% per trade
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.01, 0, 2, 1, 3, 0.05, 3));
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.01, 1.5, 2, 1, 3, 0.05, 0));
    }
}
