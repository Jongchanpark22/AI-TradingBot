package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.ai.FeatureSnapshotFactory;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategyRegistry;
import com.example.cryptobot.strategy.core.StrategyRunLogService;
import com.example.cryptobot.strategy.core.StrategySignal;
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
import java.util.*;

/**
 * 독립 실행 멀티전략 백테스트 엔진.
 *
 * <p>ML 메타라벨링 학습 데이터 생성 전용. 모든 전략(5개 룰기반 + HYBRID)이
 * 매 봉마다 독립적으로 신호를 평가하고, 각각 별도의 포지션을 시뮬레이션한다.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>레짐 게이팅 없음 — 모든 레짐에서 모든 전략 실행 (전략×레짐 조합 데이터 수집)</li>
 *   <li>전략별 독립 포지션: Map&lt;strategyId, OpenPosition&gt;으로 동시 보유</li>
 *   <li>신호 1건 = signalId 1개 = strategy_run_logs + trade_history 연결</li>
 *   <li>수수료·슬리피지: 왕복 비용이 PnL(라벨)에 반영됨</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiStrategyBacktestEngine {

    private final StrategyRegistry strategyRegistry;
    private final StrategyRunLogService strategyRunLogService;
    private final TradeHistoryRepository tradeHistoryRepository;

    @Value("${backtest.fee-rate:0.0005}")
    private double feeRate;

    @Value("${backtest.slippage-rate:0.0003}")
    private double slippageRate;

    // ATR 기반 리스크 파라미터 (application.yml trading.risk.*)
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

    @Value("${trading.risk.partial2-r:2.0}")
    private double partial2ExitR;

    @Value("${trading.risk.partial2-ratio:0.5}")
    private double partial2ExitRatio;

    @Value("${trading.risk.trail-atr-mult-after-p2:1.5}")
    private double trailAtrMultAfterP2;

    // 시장 맥락 필터 (Task 2)
    @Value("${trading.market-filter.enabled:true}")
    private boolean marketFilterEnabled;

    @Value("${trading.market-filter.block-when-market-downtrend:true}")
    private boolean blockWhenMarketDowntrend;

    // 다중 타임프레임 필터 (Task 3)
    @Value("${trading.mtf.enabled:true}")
    private boolean mtfFilterEnabled;

    @Value("${trading.mtf.use-1h-filter:true}")
    private boolean use1hFilter;

    @Value("${trading.mtf.use-4h-filter:false}")
    private boolean use4hFilter;

    private static final int WINDOW_SIZE = 70;  // 가장 긴 전략(RsiDivergence 63봉) 커버

    private static final String HYBRID_STRATEGY_ID   = "HYBRID";
    private static final String HYBRID_STRATEGY_TYPE = "COMPOSITE";

    private final HybridSignalAnalyzer hybridAnalyzer = new HybridSignalAnalyzer();
    private final RegimeClassifier regimeClassifier   = new RegimeClassifier();

    /**
     * 단일 심볼에 대해 모든 전략을 독립 실행한다.
     *
     * @param symbol     심볼 (예: KRW-BTC)
     * @param candles    오름차순 정렬된 15분봉 캔들
     * @param btcCandles BTC 15분봉 (시장 맥락·MTF 계산용, null이면 필터 스킵)
     * @param timeframe  타임프레임 이름 (예: "15분")
     * @param account    백테스트 계정 (null이면 trade_history 저장 생략)
     * @return 백테스트 요약 (전체 거래 합산)
     */
    @Transactional
    public BacktestResult run(String symbol, List<Candle> candles,
                               @Nullable List<Candle> btcCandles,
                               String timeframe,
                               @Nullable Account account) {
        if (candles == null || candles.size() <= WINDOW_SIZE + 1) {
            log.warn("[MultiStrategyBacktest] 캔들 부족: {} — {}개", symbol,
                    candles == null ? 0 : candles.size());
            return emptyResult();
        }

        List<Strategy> strategies = strategyRegistry.all();
        Map<String, OpenPosition> positions = new HashMap<>();
        List<BacktestTrade> trades = new ArrayList<>();

        double equity = 1_000_000.0;
        double peakEquity = equity;
        double maxDrawdown = 0.0;
        List<Double> equityCurve = new ArrayList<>();

        // Task 3: 1h/4h 집계 사전 계산 (O(n))
        List<double[]> h1Agg = preAggregateCandles(candles, 60);
        List<double[]> h4Agg = preAggregateCandles(candles, 240);
        int h1Ptr = 0, h4Ptr = 0;

        // Task 2: BTC 캔들 포인터
        List<Candle> safeBtc = btcCandles != null ? btcCandles : List.of();
        int btcPtr = 0;

        for (int i = WINDOW_SIZE - 1; i < candles.size(); i++) {
            List<Candle> window = candles.subList(0, i + 1);
            Candle bar = candles.get(i);

            // Task 3: 완성된 1h/4h 봉 포인터 업데이트 (bar i 이전까지만)
            while (h1Ptr < h1Agg.size() && (int) h1Agg.get(h1Ptr)[1] < i) h1Ptr++;
            while (h4Ptr < h4Agg.size() && (int) h4Agg.get(h4Ptr)[1] < i) h4Ptr++;

            // Task 2: BTC 포인터 업데이트 (bar i의 timestamp 이하인 BTC 봉까지)
            LocalDateTime barTime = bar.getTimestamp();
            if (barTime != null) {
                while (btcPtr < safeBtc.size() - 1) {
                    LocalDateTime nextBtcTs = safeBtc.get(btcPtr + 1).getTimestamp();
                    if (nextBtcTs != null && !nextBtcTs.isAfter(barTime)) btcPtr++;
                    else break;
                }
            }

            // ----- 열린 포지션 관리 -----
            double high  = val(bar.getHighPrice());
            double low   = val(bar.getLowPrice());
            double close = val(bar.getClosePrice());
            double atr   = Indicators.atr(window, 14);

            Iterator<Map.Entry<String, OpenPosition>> posIter = positions.entrySet().iterator();
            while (posIter.hasNext()) {
                Map.Entry<String, OpenPosition> e = posIter.next();
                OpenPosition pos = e.getValue();
                if (Double.isNaN(atr) || atr <= 0) atr = pos.atrAtEntry;

                pos.highestSeen = Math.max(pos.highestSeen, high);

                if (low <= pos.currentStop) {
                    equity = closeAndLog(pos, pos.currentStop, bar, "stop-loss",
                            account, symbol, equity, trades);
                    posIter.remove();
                } else if (pos.takeProfit > 0 && high >= pos.takeProfit) {
                    // 하드 익절 (take-profit-r > 0일 때만)
                    equity = closeAndLog(pos, pos.takeProfit, bar, "take-profit",
                            account, symbol, equity, trades);
                    posIter.remove();
                } else {
                    double initRisk = pos.entryPrice - pos.initialStop;
                    // 1차 부분청산: +partialExitR 달성 시 partialExitRatio 비율 청산
                    if (!pos.partialDone && initRisk > 0
                            && close >= pos.entryPrice + partialExitR * initRisk) {
                        double partialQty = pos.quantity * partialExitRatio;
                        equity += (close - pos.entryPrice) * partialQty;
                        pos.quantity -= partialQty;
                        pos.partialDone = true;
                        pos.currentStop = Math.max(pos.currentStop, pos.entryPrice);
                    }
                    // 2차 부분청산: +partial2ExitR 달성 시 남은 수량의 partial2ExitRatio 청산 (1차 완료 후)
                    if (pos.partialDone && !pos.secondPartialDone && initRisk > 0 && partial2ExitR > 0
                            && close >= pos.entryPrice + partial2ExitR * initRisk) {
                        double partial2Qty = pos.quantity * partial2ExitRatio;
                        equity += (close - pos.entryPrice) * partial2Qty;
                        pos.quantity -= partial2Qty;
                        pos.secondPartialDone = true;
                    }
                    // 챈들리어 트레일링 (2차 도달 후 trailAtrMultAfterP2 강화, 미달 시 기본 trailAtrMult)
                    if (pos.highestSeen > pos.entryPrice) {
                        double activeMult = pos.secondPartialDone ? trailAtrMultAfterP2 : trailAtrMult;
                        double chandelier = pos.highestSeen - activeMult * atr;
                        pos.currentStop = Math.max(pos.currentStop, chandelier);
                    }
                }
            }

            // ----- 신호 평가 (다음 봉 있을 때만 진입) -----
            if (i + 1 < candles.size()) {
                // Task 2: BTC 시장 맥락 계산
                int btcEnd = safeBtc.isEmpty() ? 0 : btcPtr + 1;
                int btcStart = Math.max(0, btcEnd - 50);
                List<Candle> btcWindow = safeBtc.isEmpty()
                        ? List.of()
                        : safeBtc.subList(btcStart, btcEnd);
                BtcContext btcCtx = computeBtcContext(btcWindow);

                // Task 2: BTC 하락장 → 이 봉의 모든 롱 진입 차단
                if (marketFilterEnabled && blockWhenMarketDowntrend
                        && "TRENDING_DOWN".equals(btcCtx.marketRegime())) {
                    // 포지션 관리는 계속, 신호 평가만 스킵
                    double mtm2 = equity;
                    for (OpenPosition pos : positions.values()) {
                        mtm2 += (close - pos.entryPrice) * pos.quantity;
                    }
                    peakEquity = Math.max(peakEquity, mtm2);
                    double dd = peakEquity > 0 ? (peakEquity - mtm2) / peakEquity : 0;
                    if (dd > maxDrawdown) maxDrawdown = dd;
                    equityCurve.add(mtm2);
                    continue;
                }

                // Task 3: 1h/4h MTF 필터
                boolean h1Bullish = !mtfFilterEnabled || !use1hFilter
                        || isHigherTFBullish(h1Agg, h1Ptr, 12, 26);
                boolean h4Bullish = !mtfFilterEnabled || !use4hFilter
                        || isHigherTFBullish(h4Agg, h4Ptr, 12, 26);

                // 시장 컨텍스트 공통 계산
                MarketContext ctx = buildMarketContext(symbol, timeframe, window, btcCtx);

                // (A) 룰기반 전략 5개 독립 평가
                for (Strategy strategy : strategies) {
                    String sid = strategy.id();
                    if (positions.containsKey(sid)) continue;

                    Optional<StrategySignal> sigOpt = strategy.evaluate(window);
                    if (sigOpt.isEmpty()) continue;

                    // Task 3: MTF 필터 적용
                    if (!h1Bullish || !h4Bullish) continue;

                    StrategySignal sig = sigOpt.get();
                    String signalId = UUID.randomUUID().toString();

                    FeatureSnapshot snap = buildSnapshot(ctx, sig.strategyId(), sig.strategyType().name());
                    strategyRunLogService.save(
                            signalId, snap, null,
                            symbol, timeframe,
                            ctx.ema12(), ctx.ema26(), ctx.sma50(),
                            ctx.rsi14(), ctx.volumeRatio(),
                            ctx.trend(), ctx.momentum(), ctx.rsiSig(), ctx.volumeSig(), ctx.candleSig(),
                            "BUY", 100, "전략 신호: " + sid,
                            true, null,
                            "BACKTEST", sid);

                    Candle nextBar = candles.get(i + 1);
                    double fill = val(nextBar.getOpenPrice());
                    double entryAtr = sig.atr() > 0 ? sig.atr() : (fill * 0.01);
                    double stopDist = slAtrMult * entryAtr;
                    double stopLoss = fill - stopDist;
                    double stopPct  = fill > 0 ? stopDist / fill : 0;
                    if (stopPct < 0.02 || stopPct > 0.10) { stopLoss = fill * 0.95; stopDist = fill - stopLoss; }
                    double takeProfit = tpAtrR > 0 ? fill + tpAtrR * stopDist : 0; // 0=트레일링만

                    double riskKrw = equity * 0.01;
                    double qty = stopDist > 0 ? riskKrw / stopDist : 0;
                    if (qty > 0) {
                        positions.put(sid, new OpenPosition(
                                signalId, sid, nextBar.getTimestamp(),
                                fill, qty, stopLoss, takeProfit, entryAtr));
                    }
                }

                // (B) HYBRID 전략 평가 (HybridSignalAnalyzer)
                if (!positions.containsKey(HYBRID_STRATEGY_ID) && ctx.rawHybridBuy()
                        && h1Bullish && h4Bullish) {
                    String signalId = UUID.randomUUID().toString();
                    FeatureSnapshot snap = buildSnapshot(ctx, HYBRID_STRATEGY_ID, HYBRID_STRATEGY_TYPE);
                    strategyRunLogService.save(
                            signalId, snap, null,
                            symbol, timeframe,
                            ctx.ema12(), ctx.ema26(), ctx.sma50(),
                            ctx.rsi14(), ctx.volumeRatio(),
                            ctx.trend(), ctx.momentum(), ctx.rsiSig(), ctx.volumeSig(), ctx.candleSig(),
                            ctx.tradeSignal().getSignal().name(),
                            ctx.tradeSignal().getConfidence(),
                            ctx.tradeSignal().getReason(),
                            true, null,
                            "BACKTEST", HYBRID_STRATEGY_ID);

                    Candle nextBar = candles.get(i + 1);
                    double fill = val(nextBar.getOpenPrice());
                    double atrVal = Indicators.atr(window, 14);
                    if (Double.isNaN(atrVal) || atrVal <= 0) atrVal = fill * 0.01;
                    double stopDist = slAtrMult * atrVal;
                    double stopLoss = fill - stopDist;
                    double stopPct  = fill > 0 ? stopDist / fill : 0;
                    if (stopPct < 0.02 || stopPct > 0.10) { stopLoss = fill * 0.95; stopDist = fill - stopLoss; }
                    double takeProfit = tpAtrR > 0 ? fill + tpAtrR * stopDist : 0;

                    double riskKrw = equity * 0.01;
                    double qty = stopDist > 0 ? riskKrw / stopDist : 0;
                    if (qty > 0) {
                        positions.put(HYBRID_STRATEGY_ID, new OpenPosition(
                                signalId, HYBRID_STRATEGY_ID, nextBar.getTimestamp(),
                                fill, qty, stopLoss, takeProfit, atrVal));
                    }
                }
            }

            // 마크투마켓 (보유 포지션 전체)
            double mtm = equity;
            for (OpenPosition pos : positions.values()) {
                mtm += (close - pos.entryPrice) * pos.quantity;
            }
            peakEquity = Math.max(peakEquity, mtm);
            double dd = peakEquity > 0 ? (peakEquity - mtm) / peakEquity : 0;
            if (dd > maxDrawdown) maxDrawdown = dd;
            equityCurve.add(mtm);
        }

        // 마지막 열린 포지션 강제 청산
        Candle lastBar = candles.get(candles.size() - 1);
        for (OpenPosition pos : positions.values()) {
            equity = closeAndLog(pos, val(lastBar.getClosePrice()), lastBar,
                    "end-of-data", account, symbol, equity, trades);
        }
        positions.clear();

        return summarize(1_000_000.0, equity, maxDrawdown, trades, equityCurve);
    }

    // ============================================================
    // 시장 컨텍스트 계산
    // ============================================================

    private MarketContext buildMarketContext(String symbol, String timeframe, List<Candle> window,
                                              BtcContext btcCtx) {
        List<Double> closes = window.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
        double sma50 = TechnicalIndicatorCalculator.calculateSMA(closes, 50);

        TechnicalIndicatorCalculator.MACDValues macd = TechnicalIndicatorCalculator.calculateMACD(closes);
        double macdVal = 0, macdSig = 0, macdHist = 0, prevMacd = 0, prevMacdSig = 0;
        if (macd != null) {
            macdVal     = macd.getMacd();
            macdSig     = macd.getSignalLine();
            macdHist    = macd.getHistogram();
            prevMacd    = macd.getPreviousMacd();
            prevMacdSig = macd.getPreviousSignalLine();
        }

        double rsi14 = TechnicalIndicatorCalculator.calculateRSI(closes, 14);
        double atr14 = Indicators.atr(window, 14);
        if (Double.isNaN(atr14)) atr14 = 0.0;

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
        HybridSignalAnalyzer.TrendSignal trend = hybridAnalyzer.analyzeTrend(ema12, ema26, sma50);
        HybridSignalAnalyzer.MomentumSignal momentum = hybridAnalyzer.analyzeMacd(
                macdVal, macdSig, prevMacd, prevMacdSig);
        HybridSignalAnalyzer.RSISignal rsiSig = hybridAnalyzer.analyzeRSI(rsi14);
        HybridSignalAnalyzer.VolumeSignal volumeSig = hybridAnalyzer.analyzeVolume(curVol, volMA20);

        Candle completed = window.size() >= 2 ? window.get(window.size() - 2) : window.get(window.size() - 1);
        HybridSignalAnalyzer.CandleSignal candleSig = hybridAnalyzer.analyzeCandlePattern(
                val(completed.getOpenPrice()), val(completed.getHighPrice()),
                val(completed.getLowPrice()), val(completed.getClosePrice()));
        if (window.size() >= 3) {
            Candle prev2 = window.get(window.size() - 3);
            boolean curBull  = completed.getClosePrice() != null && completed.getOpenPrice() != null
                    && completed.getClosePrice().compareTo(completed.getOpenPrice()) > 0;
            boolean prevBull = prev2.getClosePrice() != null && prev2.getOpenPrice() != null
                    && prev2.getClosePrice().compareTo(prev2.getOpenPrice()) > 0;
            if (curBull && prevBull) candleSig = HybridSignalAnalyzer.CandleSignal.CONSECUTIVE_BULLISH;
        }

        HybridSignalAnalyzer.TradeSignal tradeSignal = hybridAnalyzer.generateTradeSignal(
                trend, momentum, rsiSig, volumeSig, candleSig);
        boolean rawHybridBuy = tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY;

        return new MarketContext(symbol, timeframe, window,
                ema12, ema26, sma50,
                macdVal, macdSig, macdHist, rsi14, volumeRatio, atr14,
                regime, trend, momentum, rsiSig, volumeSig, candleSig, tradeSignal, rawHybridBuy,
                btcCtx.marketRegime(), btcCtx.marketTrend(),
                btcCtx.marketReturn(), btcCtx.marketAboveMA());
    }

    private FeatureSnapshot buildSnapshot(MarketContext ctx, String strategyId, String strategyType) {
        return FeatureSnapshotFactory.build(
                ctx.symbol(), ctx.timeframe(), ctx.window(),
                ctx.ema12(), ctx.ema26(), ctx.sma50(),
                ctx.macd(), ctx.macdSignal(), ctx.macdHist(),
                ctx.rsi14(), ctx.volumeRatio(), ctx.atr14(),
                ctx.regime(), ctx.trend(), ctx.momentum(), ctx.rsiSig(), ctx.volumeSig(), ctx.candleSig(),
                ctx.tradeSignal(), strategyId, strategyType,
                ctx.mktRegime(), ctx.mktTrend(), ctx.mktReturn(), ctx.mktAboveMA());
    }

    // ============================================================
    // BTC 시장 맥락 계산 (Task 2)
    // ============================================================

    /**
     * BTC 15분봉 윈도우에서 시장 맥락을 계산한다.
     * 데이터 부족 시 UNKNOWN 반환 (필터 스킵).
     */
    private BtcContext computeBtcContext(List<Candle> btcWindow) {
        if (btcWindow.size() < 26) return BtcContext.UNKNOWN;

        List<Double> closes = btcWindow.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
        int mktTrend = ema12 > ema26 ? 1 : 0;

        MarketRegime regime = regimeClassifier.classify(btcWindow);

        double mktReturn = 0.0;
        if (closes.size() >= 21) {
            double recent = closes.get(closes.size() - 1);
            double past   = closes.get(closes.size() - 21);
            mktReturn = past > 0 ? (recent - past) / past * 100.0 : 0.0;
        }

        double sma50 = closes.size() >= 50
                ? TechnicalIndicatorCalculator.calculateSMA(closes, 50) : 0.0;
        int mktAboveMA = sma50 > 0 && closes.get(closes.size() - 1) > sma50 ? 1 : 0;

        return new BtcContext(regime.name(), mktTrend, mktReturn, mktAboveMA);
    }

    // ============================================================
    // 1h/4h 집계 & EMA 필터 (Task 3)
    // ============================================================

    /**
     * 15분봉을 상위 타임프레임으로 집계한다.
     * 반환: {종가, 마지막15m봉_인덱스} — 완성된 봉만 포함.
     *
     * @param periodMinutes 60=1h, 240=4h
     */
    private static List<double[]> preAggregateCandles(List<Candle> candles, int periodMinutes) {
        List<double[]> result = new ArrayList<>();
        long curPeriodKey = -1;
        double curClose = 0.0;
        int lastIdx = -1;

        for (int i = 0; i < candles.size(); i++) {
            LocalDateTime ts = candles.get(i).getTimestamp();
            if (ts == null) continue;
            long periodKey = ts.toEpochSecond(ZoneOffset.UTC) / (periodMinutes * 60L);

            if (periodKey != curPeriodKey) {
                if (curPeriodKey >= 0 && lastIdx >= 0) {
                    result.add(new double[]{curClose, lastIdx});
                }
                curPeriodKey = periodKey;
            }
            curClose = val(candles.get(i).getClosePrice());
            lastIdx = i;
        }
        // 마지막 봉은 미완성일 수 있으므로 포함하지 않음
        return result;
    }

    /**
     * 완성된 상위봉 목록(count개)에서 EMA fast > EMA slow 여부를 판단한다.
     * 데이터 부족 시 true (필터 스킵, 매수 허용).
     */
    private static boolean isHigherTFBullish(List<double[]> agg, int count, int fast, int slow) {
        if (count < slow) return true;
        int start = Math.max(0, count - slow);
        List<Double> closes = new ArrayList<>(slow);
        for (int k = start; k < count; k++) closes.add(agg.get(k)[0]);
        if (closes.size() < slow) return true;
        double emaFast = TechnicalIndicatorCalculator.calculateEMA(closes, fast);
        double emaSlow = TechnicalIndicatorCalculator.calculateEMA(closes, slow);
        return emaFast >= emaSlow;
    }

    // ============================================================
    // 포지션 청산 + 로그 저장
    // ============================================================

    private double closeAndLog(OpenPosition pos, double exitPrice, Candle exitBar, String reason,
                                @Nullable Account account, String symbol,
                                double equity, List<BacktestTrade> trades) {
        // 수수료·슬리피지 왕복 적용
        double costPerSide    = feeRate + slippageRate;
        double effectiveEntry = pos.entryPrice * (1.0 + costPerSide);
        double effectiveExit  = exitPrice      * (1.0 - costPerSide);
        double pnl = (effectiveExit - effectiveEntry) * pos.quantity;
        double r   = (pos.entryPrice - pos.initialStop) > 0
                ? (exitPrice - pos.entryPrice) / (pos.entryPrice - pos.initialStop) : 0;

        trades.add(new BacktestTrade(
                symbol, pos.strategyId, pos.entryTime, pos.entryPrice, pos.quantity,
                pos.initialStop, exitBar.getTimestamp(), exitPrice, reason, pnl, r));

        if (account != null) {
            TradeHistory.ExitType exitType = resolveExitType(reason);
            BigDecimal entryBD = BigDecimal.valueOf(pos.entryPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal exitBD  = BigDecimal.valueOf(exitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal qtyBD   = BigDecimal.valueOf(pos.quantity).setScale(8, RoundingMode.HALF_UP);
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

            tradeHistoryRepository.save(TradeHistory.builder()
                    .account(account)
                    .symbol(symbol)
                    .entryPrice(entryBD).exitPrice(exitBD).quantity(qtyBD)
                    .entryAmount(entryAmt).exitAmount(exitAmt)
                    .profitAmount(profitAmt).profitRate(profitRate)
                    .entryTime(pos.entryTime).exitTime(exitBar.getTimestamp())
                    .exitType(exitType).exitReason(reason)
                    .atrAtEntry(pos.atrAtEntry).highestPrice(pos.highestSeen)
                    .partialExit(pos.partialDone)
                    .signalId(pos.signalId)
                    .source("BACKTEST")
                    .build());
        }
        return equity + pnl;
    }

    private static TradeHistory.ExitType resolveExitType(String reason) {
        if (reason == null) return TradeHistory.ExitType.MANUAL;
        return switch (reason) {
            case "stop-loss"   -> TradeHistory.ExitType.STOP_LOSS;
            case "take-profit" -> TradeHistory.ExitType.TAKE_PROFIT;
            default            -> TradeHistory.ExitType.SELL_SIGNAL;
        };
    }

    // ============================================================
    // 요약
    // ============================================================

    private static BacktestResult summarize(double start, double end, double maxDd,
                                             List<BacktestTrade> trades, List<Double> curve) {
        int total = trades.size(), wins = 0;
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

    private static double val(java.math.BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }

    // ============================================================
    // 내부 타입
    // ============================================================

    private static final class OpenPosition {
        final String signalId;
        final String strategyId;
        final LocalDateTime entryTime;
        final double entryPrice;
        final double initialStop;
        double currentStop;
        final double takeProfit;
        final double atrAtEntry;
        double highestSeen;
        double quantity;
        boolean partialDone;
        boolean secondPartialDone;

        OpenPosition(String signalId, String strategyId, LocalDateTime entryTime, double entryPrice,
                     double quantity, double initialStop, double takeProfit, double atrAtEntry) {
            this.signalId    = signalId;
            this.strategyId  = strategyId;
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

    /** BTC 시장 맥락 데이터 (Task 2). */
    private record BtcContext(
            String marketRegime, int marketTrend, double marketReturn, int marketAboveMA) {
        static final BtcContext UNKNOWN = new BtcContext("UNKNOWN", 0, 0.0, 0);
    }

    /** 한 봉에서 한 번만 계산하는 시장 공통 지표 묶음 (BTC 맥락 포함). */
    private record MarketContext(
            String symbol, String timeframe, List<Candle> window,
            double ema12, double ema26, double sma50,
            double macd, double macdSignal, double macdHist,
            double rsi14, double volumeRatio, double atr14,
            MarketRegime regime,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSig,
            HybridSignalAnalyzer.VolumeSignal volumeSig,
            HybridSignalAnalyzer.CandleSignal candleSig,
            HybridSignalAnalyzer.TradeSignal tradeSignal,
            boolean rawHybridBuy,
            String mktRegime, int mktTrend, double mktReturn, int mktAboveMA) {}
}
