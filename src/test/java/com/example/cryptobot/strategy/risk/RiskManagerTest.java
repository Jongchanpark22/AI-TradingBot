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
        // equity 10,000, risk 1% -> 100 at risk
        // ATR 2.0, stopAtrMult 3.0 -> stop distance 6.0
        // qty = 100 / 6 ≈ 16.667, takeProfit = 100 + 6×3 = 118
        EntryPlan p = risk.planLong(10_000, 100.0, 2.0);
        assertTrue(p.isExecutable());
        assertEquals(100.0 - 6.0, p.stopLossPrice(), 1e-9);
        assertEquals(100.0 + 18.0, p.takeProfitPrice(), 1e-9); // TP = entry + stopDist × takeProfitR = 100+6×3
        assertEquals(100.0 / 6.0, p.quantity(), 1e-9);
        assertEquals(100.0, p.riskAmount(), 1e-9);
        assertEquals(3.0, p.riskRewardRatio(), 1e-9); // defaults takeProfitRMultiple=3.0
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
                100, 97, 97, 96.5, 100, 2.0, false, false);
        assertTrue(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
    }

    @Test
    void trailingPartialExitFiresAtOneRAndMovesStopToBreakEven() {
        // entry 100, stop 97 -> R=3 -> 1차 partial at 103
        TrailingDecision d = risk.updateTrailing(
                100, 97, 97, 103.0, 103.0, 2.0, false, false);
        assertFalse(d.shouldExitNow());
        assertTrue(d.shouldPartialExit());
        assertFalse(d.shouldSecondPartialExit());
        assertEquals(100.0, d.newStopLoss(), 1e-9, "stop should move to break-even");
    }

    @Test
    void secondPartialExitFiresAt2RAfterFirstPartial() {
        // entry 100, stop 97 -> R=3 -> 2차 partial at +2R=106 (1차 완료 상태)
        TrailingDecision d = risk.updateTrailing(
                100, 97, 100, 106.0, 106.0, 2.0, true, false);
        assertFalse(d.shouldExitNow());
        assertFalse(d.shouldPartialExit());
        assertTrue(d.shouldSecondPartialExit());
    }

    @Test
    void chandelierTrailRatchetsStopUpButNeverDown() {
        // 1차 완료(partialDone=true), 2차 미도달(price=105 < +2R=106) → 기본 트레일링 3.0×ATR
        // highest=110, ATR=1 -> chandelier=107
        TrailingDecision up = risk.updateTrailing(
                100, 97, 100, 105, 110, 1.0, true, false);
        assertEquals(107.0, up.newStopLoss(), 1e-9);

        // 가격 하락해도 stop은 내려가지 않음 (단조증가 보장)
        TrailingDecision pullback = risk.updateTrailing(
                100, 97, 107, 105, 110, 1.0, true, false);
        assertEquals(107.0, pullback.newStopLoss(), 1e-9);
    }

    @Test
    void chandelierTightensAfterSecondPartial() {
        // 2차 완료(secondPartialDone=true) → 강화 트레일링 1.5×ATR
        // highest=110, ATR=1 -> chandelier=108.5
        TrailingDecision d = risk.updateTrailing(
                100, 97, 100, 109, 110, 1.0, true, true);
        assertEquals(108.5, d.newStopLoss(), 1e-9);
    }

    @Test
    void trailingHoldsWhenNothingHappens() {
        TrailingDecision d = risk.updateTrailing(
                100, 97, 97, 100.5, 100.5, 2.0, false, false);
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
        // riskPerTrade=0 → 거부
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0, 1.5, 2, 1, 0.5, 2.0, 0.5, 3, 1.5, 0.05, 3));
        // riskPerTrade=0.5 → 10% 초과 → 거부
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.5, 1.5, 2, 1, 0.5, 2.0, 0.5, 3, 1.5, 0.05, 3));
        // stopAtrMultiplier=0 → 거부
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.01, 0, 2, 1, 0.5, 2.0, 0.5, 3, 1.5, 0.05, 3));
        // maxOpenPositions=0 → 거부
        assertThrows(IllegalArgumentException.class, () ->
                new RiskParameters(0.01, 1.5, 2, 1, 0.5, 2.0, 0.5, 3, 1.5, 0.05, 0));
    }
}
