package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.ai.FeatureSnapshotFactory;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import com.example.cryptobot.strategy.core.StrategyRunLogService;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import com.example.cryptobot.strategy.hybrid.TechnicalIndicatorCalculator;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.regime.MarketRegime;
import com.example.cryptobot.strategy.regime.RegimeClassifier;
import com.example.cryptobot.trade.TradeHistory;
import com.example.cryptobot.trade.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HybridSignalAnalyzer 기반 백테스트 엔진.
 *
 * <p>BacktestEngine(RegimeRouter 기반)과 달리, 라이브 실행부(HybridStrategyExecutor)와
 * 동일한 신호 엔진(HybridSignalAnalyzer)으로 진입을 결정한다.
 * 이로써 생성된 strategy_run_logs와 trade_history가 라이브 추론에 사용될 ML 모델의
 * 학습 데이터로 적합하다(학습·추론 분포 일치).
 *
 * <p>설계 제약:
 * <ul>
 *   <li>Look-ahead 금지: 신호 발생 봉의 다음 봉 시가에 체결 (BacktestEngine 동일 관행)</li>
 *   <li>다중 타임프레임 필터 미적용: 백테스트는 15분봉 단일 타임프레임만 사용 (1h/4h DB 접근 불가)</li>
 *   <li>source='BACKTEST': strategy_run_logs, trade_history 양쪽에 설정</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridBacktestEngine {

    private final StrategyRunLogService strategyRunLogService;
    private final TradeHistoryRepository tradeHistoryRepository;

    private static final int WINDOW_SIZE = 50;
    private static final String STRATEGY_ID   = "HYBRID";
    private static final String STRATEGY_TYPE = "COMPOSITE";

    @Value("${backtest.fee-rate:0.0005}")
    private double feeRate;

    @Value("${backtest.slippage-rate:0.0003}")
    private double slippageRate;

    @Value("${trading.risk.stop-atr-mult:3.0}")
    private double slAtrMult;

    @Value("${trading.risk.take-profit-r:3.0}")
    private double tpAtrR;

    @Value("${trading.risk.trail-atr-mult:3.0}")
    private double trailAtrMult;

    @Value("${trading.risk.partial-exit-r:1.0}")
    private double partialExitR;

    @Value("${trading.risk.partial-exit-ratio:0.5}")
    private double partialExitRatio;

    private final HybridSignalAnalyzer signalAnalyzer = new HybridSignalAnalyzer();
    private final RegimeClassifier regimeClassifier = new RegimeClassifier();

    /**
     * 단일 심볼 백테스트 실행.
     *
     * @param symbol    심볼 (예: KRW-BTC)
     * @param candles   오름차순 정렬된 15분봉 캔들 리스트
     * @param timeframe 타임프레임 이름 (예: "15분")
     * @param account   백테스트 거래 계정 (null이면 trade_history 저장 건너뜀)
     * @return 백테스트 요약 결과
     */
    @Transactional
    public BacktestResult run(String symbol, List<Candle> candles,
                               @Nullable List<Candle> btcCandles,
                               String timeframe,
                               @Nullable Account account) {
        if (candles == null || candles.size() <= WINDOW_SIZE + 1) {
            log.warn("[HybridBacktest] 캔들 부족: {} — {}개", symbol, candles == null ? 0 : candles.size());
            return emptyResult();
        }

        List<BacktestTrade> trades = new ArrayList<>();
        List<Double> equityCurve = new ArrayList<>();
        double equity = 1_000_000.0;
        double peakEquity = equity;
        double maxDrawdown = 0.0;

        OpenPosition pos = null;

        for (int i = WINDOW_SIZE - 1; i < candles.size(); i++) {
            List<Candle> window = candles.subList(0, i + 1);
            Candle bar = candles.get(i);

            // ----- 포지션 관리 -----
            if (pos != null) {
                double high  = val(bar.getHighPrice());
                double low   = val(bar.getLowPrice());
                double close = val(bar.getClosePrice());
                double atr   = Indicators.atr(window, 14);
                if (Double.isNaN(atr) || atr <= 0) atr = pos.atrAtEntry;

                pos.highestSeen = Math.max(pos.highestSeen, high);

                // 보수적 순서: 손절 먼저, 그다음 트레일링
                if (low <= pos.currentStop) {
                    equity = closeAndLog(pos, pos.currentStop, bar, "stop-loss", account,
                            symbol, equity, trades);
                    pos = null;
                } else if (pos.takeProfit > 0 && high >= pos.takeProfit) {
                    // 하드 익절 (take-profit-r > 0일 때만)
                    equity = closeAndLog(pos, pos.takeProfit, bar, "take-profit", account,
                            symbol, equity, trades);
                    pos = null;
                } else {
                    // 부분청산: +partialExitR 달성 시 partialExitRatio 비율 청산
                    double initialRisk = pos.entryPrice - pos.initialStop;
                    if (!pos.partialDone && initialRisk > 0
                            && close >= pos.entryPrice + partialExitR * initialRisk) {
                        double partialQty = pos.quantity * partialExitRatio;
                        equity += (close - pos.entryPrice) * partialQty;
                        pos.quantity -= partialQty;
                        pos.partialDone = true;
                        pos.currentStop = Math.max(pos.currentStop, pos.entryPrice);
                    }
                    // 챈들리어 트레일링
                    if (pos.highestSeen > pos.entryPrice) {
                        double chandelier = pos.highestSeen - trailAtrMult * atr;
                        double newStop = Math.max(pos.currentStop, chandelier);
                        if (newStop > pos.currentStop) {
                            pos.currentStop = newStop;
                        }
                    }
                }
            }

            // ----- 신호 평가 및 진입 -----
            if (pos == null && i + 1 < candles.size()) {
                SignalResult sr = evaluate(symbol, timeframe, window);

                // BUY/STRONG_BUY raw 신호만 로그 저장 대상
                if (sr.rawBuy) {
                    String signalId = UUID.randomUUID().toString();

                    // 모든 BUY 신호 strategy_run_logs에 기록 (차단 여부와 관계없이)
                    strategyRunLogService.save(
                            signalId, sr.snap, null,
                            symbol, timeframe,
                            sr.ema12, sr.ema26, sr.sma50,
                            sr.rsi14, sr.volumeRatio,
                            sr.trend, sr.momentum, sr.rsiSig, sr.volumeSig, sr.candleSig,
                            sr.finalSignal.getSignal().name(),
                            sr.finalSignal.getConfidence(),
                            sr.finalSignal.getReason(),
                            sr.finalSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                                    || sr.finalSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY,
                            sr.blockedReason,
                            "BACKTEST",
                            STRATEGY_ID);

                    // 필터 통과 신호만 포지션 진입
                    boolean enters = sr.finalSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                            || sr.finalSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY;
                    if (enters) {
                        Candle nextBar = candles.get(i + 1);
                        double fill = val(nextBar.getOpenPrice());
                        double atr  = sr.atr14;
                        if (atr <= 0) atr = fill * 0.01; // ATR 없으면 1% 폴백

                        double stopDist = slAtrMult * atr;
                        double stopLoss = fill - stopDist;
                        double stopPct  = fill > 0 ? stopDist / fill : 0;
                        if (stopPct < 0.02 || stopPct > 0.10) {
                            stopLoss = fill * 0.95;
                            stopDist = fill - stopLoss;
                        }
                        double takeProfit = tpAtrR > 0 ? fill + tpAtrR * stopDist : 0;

                        double riskKrw  = equity * 0.01;
                        double quantity = stopDist > 0 ? riskKrw / stopDist : 0;

                        if (quantity > 0) {
                            pos = new OpenPosition(signalId, nextBar.getTimestamp(),
                                    fill, quantity, stopLoss, takeProfit, atr);
                        }
                    }
                }
            }

            // 마크투마켓
            double mtm = equity;
            if (pos != null) {
                mtm += (val(bar.getClosePrice()) - pos.entryPrice) * pos.quantity;
            }
            peakEquity = Math.max(peakEquity, mtm);
            double dd = peakEquity > 0 ? (peakEquity - mtm) / peakEquity : 0;
            if (dd > maxDrawdown) maxDrawdown = dd;
            equityCurve.add(mtm);
        }

        // 마지막 열린 포지션 강제 청산
        if (pos != null) {
            Candle last = candles.get(candles.size() - 1);
            equity = closeAndLog(pos, val(last.getClosePrice()), last,
                    "end-of-data", account, symbol, equity, trades);
        }

        return summarize(1_000_000.0, equity, maxDrawdown, trades, equityCurve);
    }

    // ============================================================
    // 신호 평가
    // ============================================================

    private SignalResult evaluate(String symbol, String timeframe, List<Candle> window) {
        List<Double> closes = window.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
        double sma50 = TechnicalIndicatorCalculator.calculateSMA(closes, 50);

        TechnicalIndicatorCalculator.MACDValues macd = TechnicalIndicatorCalculator.calculateMACD(closes);
        if (macd == null) return SignalResult.noSignal();

        double rsi14 = TechnicalIndicatorCalculator.calculateRSI(closes, 14);
        double atr14 = Indicators.atr(window, 14);
        if (Double.isNaN(atr14)) atr14 = 0.0;

        // 거래량 (완성된 캔들 기준)
        List<Double> vols = window.stream()
                .map(c -> c.getVolume() != null ? c.getVolume().doubleValue() : 0.0)
                .filter(v -> v > 0).toList();
        double volMA20 = vols.size() >= 5
                ? TechnicalIndicatorCalculator.calculateVolumeMA(vols, Math.min(20, vols.size()))
                : 0.0;
        double curVol = 0.0;
        for (int j = window.size() - 2; j >= Math.max(0, window.size() - 6); j--) {
            Double v = window.get(j).getVolume() != null ? window.get(j).getVolume().doubleValue() : null;
            if (v != null && v > 0) { curVol = v; break; }
        }
        double volumeRatio = volMA20 > 0 ? curVol / volMA20 : 0.0;

        MarketRegime regime = regimeClassifier.classify(window);

        HybridSignalAnalyzer.TrendSignal trend = signalAnalyzer.analyzeTrend(ema12, ema26, sma50);
        HybridSignalAnalyzer.MomentumSignal momentum = signalAnalyzer.analyzeMacd(
                macd.getMacd(), macd.getSignalLine(),
                macd.getPreviousMacd(), macd.getPreviousSignalLine());
        HybridSignalAnalyzer.RSISignal rsiSig = signalAnalyzer.analyzeRSI(rsi14);
        HybridSignalAnalyzer.VolumeSignal volumeSig = signalAnalyzer.analyzeVolume(curVol, volMA20);

        // 완성된 캔들로 캔들 패턴 분석
        Candle completed = window.size() >= 2 ? window.get(window.size() - 2) : window.get(window.size() - 1);
        HybridSignalAnalyzer.CandleSignal candleSig = signalAnalyzer.analyzeCandlePattern(
                val(completed.getOpenPrice()), val(completed.getHighPrice()),
                val(completed.getLowPrice()), val(completed.getClosePrice()));

        // 연속 2봉 양봉
        if (window.size() >= 3) {
            Candle prev = window.get(window.size() - 3);
            boolean curBull  = completed.getClosePrice() != null && completed.getOpenPrice() != null
                    && completed.getClosePrice().compareTo(completed.getOpenPrice()) > 0;
            boolean prevBull = prev.getClosePrice() != null && prev.getOpenPrice() != null
                    && prev.getClosePrice().compareTo(prev.getOpenPrice()) > 0;
            if (curBull && prevBull) candleSig = HybridSignalAnalyzer.CandleSignal.CONSECUTIVE_BULLISH;
        }

        HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                trend, momentum, rsiSig, volumeSig, candleSig);

        boolean rawBuy = tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY;

        // FeatureSnapshot 빌드 (rawBuy 시점에만)
        FeatureSnapshot snap = rawBuy
                ? FeatureSnapshotFactory.build(symbol, timeframe, window,
                        ema12, ema26, sma50,
                        macd.getMacd(), macd.getSignalLine(), macd.getHistogram(),
                        rsi14, volumeRatio, atr14,
                        regime, trend, momentum, rsiSig, volumeSig, candleSig, tradeSignal,
                        STRATEGY_ID, STRATEGY_TYPE)
                : null;

        // 레짐 필터 (RANGING)
        HybridSignalAnalyzer.TradeSignal finalSignal = tradeSignal;
        String blockedReason = null;
        if (rawBuy && regime == MarketRegime.RANGING) {
            finalSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0).reason("레짐 필터: RANGING").build();
            blockedReason = "RANGING";
        }

        // EMA 이격 필터 (close > ema26 × 1.03)
        double close = val(completed.getClosePrice());
        if (rawBuy && finalSignal.getSignal() != HybridSignalAnalyzer.SignalType.NO_SIGNAL
                && ema26 > 0 && close > ema26 * 1.03) {
            finalSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0).reason("EMA 이격 과도").build();
            blockedReason = "EMA_DIVERGENCE";
        }

        return new SignalResult(rawBuy, snap, ema12, ema26, sma50, rsi14, volumeRatio, atr14,
                trend, momentum, rsiSig, volumeSig, candleSig, tradeSignal, finalSignal, blockedReason);
    }

    // ============================================================
    // 포지션 청산 + 로그 저장
    // ============================================================

    private double closeAndLog(OpenPosition pos, double exitPrice, Candle exitBar, String reason,
                                @Nullable Account account, String symbol,
                                double equity, List<BacktestTrade> trades) {
        // 수수료·슬리피지: 진입 시 불리하게, 청산 시 불리하게 적용
        double costPerSide = feeRate + slippageRate;
        double effectiveEntry = pos.entryPrice * (1.0 + costPerSide);
        double effectiveExit  = exitPrice      * (1.0 - costPerSide);
        double pnl = (effectiveExit - effectiveEntry) * pos.quantity;
        double r   = (pos.entryPrice - pos.initialStop) > 0
                ? (exitPrice - pos.entryPrice) / (pos.entryPrice - pos.initialStop) : 0;

        trades.add(new BacktestTrade(
                symbol, STRATEGY_ID, pos.entryTime, pos.entryPrice, pos.quantity,
                pos.initialStop, exitBar.getTimestamp(), exitPrice, reason, pnl, r));

        // trade_history 저장 (signal_id로 strategy_run_logs와 연결)
        if (account != null) {
            TradeHistory.ExitType exitType = resolveExitType(reason);
            BigDecimal entryBD = BigDecimal.valueOf(pos.entryPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal exitBD  = BigDecimal.valueOf(exitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal qtyBD   = BigDecimal.valueOf(pos.quantity).setScale(8, RoundingMode.HALF_UP);

            // 수수료·슬리피지 반영한 실질 손익 (ML 라벨 현실성)
            BigDecimal entryAmt  = entryBD.multiply(qtyBD).setScale(2, RoundingMode.HALF_UP);
            BigDecimal exitAmt   = exitBD.multiply(qtyBD).setScale(2, RoundingMode.HALF_UP);
            BigDecimal feeSlip   = BigDecimal.valueOf(costPerSide * 2);
            BigDecimal profitAmt = exitAmt.subtract(entryAmt)
                    .subtract(entryAmt.add(exitAmt).multiply(feeSlip)
                            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            BigDecimal profitRate = entryAmt.compareTo(BigDecimal.ZERO) > 0
                    ? profitAmt.divide(entryAmt, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            TradeHistory history = TradeHistory.builder()
                    .account(account)
                    .symbol(symbol)
                    .entryPrice(entryBD)
                    .exitPrice(exitBD)
                    .quantity(qtyBD)
                    .entryAmount(entryAmt)
                    .exitAmount(exitAmt)
                    .profitAmount(profitAmt)
                    .profitRate(profitRate)
                    .entryTime(pos.entryTime)
                    .exitTime(exitBar.getTimestamp())
                    .exitType(exitType)
                    .exitReason(reason)
                    .atrAtEntry(pos.atrAtEntry)
                    .highestPrice(pos.highestSeen)
                    .partialExit(pos.partialDone)
                    .signalId(pos.signalId)
                    .source("BACKTEST")
                    .build();

            tradeHistoryRepository.save(history);
        }

        return equity + pnl;
    }

    private static TradeHistory.ExitType resolveExitType(String reason) {
        if (reason == null) return TradeHistory.ExitType.MANUAL;
        return switch (reason) {
            case "stop-loss" -> TradeHistory.ExitType.STOP_LOSS;
            case "take-profit" -> TradeHistory.ExitType.TAKE_PROFIT;
            default -> TradeHistory.ExitType.SELL_SIGNAL;
        };
    }

    // ============================================================
    // 요약
    // ============================================================

    private static BacktestResult summarize(double start, double end, double maxDd,
                                             List<BacktestTrade> trades, List<Double> curve) {
        int total = trades.size();
        int wins = 0;
        double sumR = 0, sumWin = 0, sumLossAbs = 0;
        for (BacktestTrade t : trades) {
            sumR += t.rMultiple();
            if (t.isWin()) { wins++; sumWin += t.pnl(); }
            else sumLossAbs += -t.pnl();
        }
        double winRate = total == 0 ? 0 : (double) wins / total;
        double expR    = total == 0 ? 0 : sumR / total;
        double pf      = sumLossAbs == 0 ? (sumWin > 0 ? Double.POSITIVE_INFINITY : 0) : sumWin / sumLossAbs;
        double ret     = start == 0 ? 0 : (end / start) - 1.0;
        return new BacktestResult(start, end, ret, maxDd, total, wins, winRate, expR, pf, trades, curve);
    }

    private static BacktestResult emptyResult() {
        return new BacktestResult(1_000_000, 1_000_000, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static double val(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }

    // ============================================================
    // 내부 타입
    // ============================================================

    private static final class OpenPosition {
        final String signalId;
        final LocalDateTime entryTime;
        final double entryPrice;
        final double initialStop;
        double currentStop;
        final double takeProfit;
        final double atrAtEntry;
        double highestSeen;
        double quantity;
        boolean partialDone;

        OpenPosition(String signalId, LocalDateTime entryTime, double entryPrice, double quantity,
                     double initialStop, double takeProfit, double atrAtEntry) {
            this.signalId    = signalId;
            this.entryTime   = entryTime;
            this.entryPrice  = entryPrice;
            this.quantity    = quantity;
            this.initialStop = initialStop;
            this.currentStop = initialStop;
            this.takeProfit  = takeProfit;
            this.atrAtEntry  = atrAtEntry;
            this.highestSeen = entryPrice;
        }
    }

    private record SignalResult(
            boolean rawBuy,
            FeatureSnapshot snap,
            double ema12, double ema26, double sma50,
            double rsi14, double volumeRatio, double atr14,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSig,
            HybridSignalAnalyzer.VolumeSignal volumeSig,
            HybridSignalAnalyzer.CandleSignal candleSig,
            HybridSignalAnalyzer.TradeSignal tradeSignal,
            HybridSignalAnalyzer.TradeSignal finalSignal,
            String blockedReason) {

        static SignalResult noSignal() {
            HybridSignalAnalyzer.TradeSignal ns = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL).confidence(0).reason("계산 불가").build();
            return new SignalResult(false, null, 0, 0, 0, 0, 0, 0,
                    HybridSignalAnalyzer.TrendSignal.SIDEWAYS,
                    HybridSignalAnalyzer.MomentumSignal.NEUTRAL,
                    HybridSignalAnalyzer.RSISignal.NEUTRAL,
                    HybridSignalAnalyzer.VolumeSignal.NORMAL,
                    HybridSignalAnalyzer.CandleSignal.NEUTRAL,
                    ns, ns, null);
        }
    }
}
