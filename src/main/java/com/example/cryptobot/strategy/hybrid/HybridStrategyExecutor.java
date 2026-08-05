package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.account.AccountService;
import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.service.UpbitMarketService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderService;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.risk.RiskService;
import com.example.cryptobot.strategy.ai.AiSignalGate;
import com.example.cryptobot.strategy.ai.FeatureSnapshotFactory;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import com.example.cryptobot.strategy.core.StrategyRunLogService;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.monitor.PositionMonitor;
import com.example.cryptobot.strategy.regime.MarketRegime;
import com.example.cryptobot.strategy.regime.RegimeClassifier;
import com.example.cryptobot.strategy.scanner.MarketScannerService;
import com.example.cryptobot.trade.TradeHistory;
import com.example.cryptobot.trade.TradeHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridStrategyExecutor {

    private final CandleRepository candleRepository;
    private final TickerRepository tickerRepository;
    private final PositionRepository positionRepository;
    private final OrderService orderService;
    private final RiskService riskService;
    private final StrategyRunLogService strategyRunLogService;
    private final MarketScannerService marketScannerService;
    private final AccountService accountService;
    private final UpbitMarketService upbitMarketService;
    private final UpbitApiClient upbitApiClient;
    private final PositionMonitor positionMonitor;
    private final TradeHistoryService tradeHistoryService;
    private final AiSignalGate aiSignalGate;

    // ---- 리스크 파라미터 (application.yml에서 설정) ----
    @Value("${trading.risk.stop-loss-percent:5.0}")
    private BigDecimal stopLossPercent;

    @Value("${trading.risk.take-profit-percent:10.0}")
    private BigDecimal takeProfitPercent;

    @Value("${trading.risk.max-daily-loss:10.0}")
    private BigDecimal maxDailyLoss;

    /** 기본 투자 비율 — 총 자산의 10% */
    @Value("${trading.risk.risk-per-trade:0.10}")
    private BigDecimal riskPerTrade;

    /** 최대 투자 비율 — 총 자산의 15% (STRONG_BUY 시) */
    @Value("${trading.risk.max-order-percent:0.15}")
    private BigDecimal maxOrderPercent;

    /** 최소 주문 금액 (KRW) */
    @Value("${trading.risk.min-order-amount:20000}")
    private BigDecimal minOrderAmount;

    @Value("${trading.risk.max-open-positions:3}")
    private int maxOpenPositions;

    // ATR 기반 손절·익절 배수 (백테스트·라이브 공통)
    @Value("${trading.risk.stop-atr-mult:3.0}")
    private double stopAtrMult;

    @Value("${trading.risk.take-profit-r:3.0}")
    private double takeProfitR;

    // 시장 맥락 필터 설정
    @Value("${trading.market-filter.enabled:true}")
    private boolean marketFilterEnabled;

    @Value("${trading.market-filter.block-when-market-downtrend:true}")
    private boolean blockWhenMarketDowntrend;

    @Value("${trading.market-filter.market-symbol:KRW-BTC}")
    private String marketSymbol;

    /** true: 업비트에 실제 주문 제출 / false(기본): 신호·로그만, 주문 없음 */
    @Value("${trading.execution.enabled:false}")
    private boolean executionEnabled;

    /** false: 15분 전략 스케줄러 전체 중지 (자동매매 비서 피벗 후 기본값 false) */
    @Value("${trading.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    private final HybridSignalAnalyzer signalAnalyzer = new HybridSignalAnalyzer();
    private final TradeExecutionEngine executionEngine = new TradeExecutionEngine();
    private final RegimeClassifier regimeClassifier = new RegimeClassifier();

    // ---- 스케줄러 ----

    /** 15분마다 실행 — 15분봉 50개 기준으로 신호 분석 (노이즈 감소) */
    @Scheduled(cron = "0 0/15 * * * *")
    public void executeHybridStrategy15Min() {
        // trading.scheduler.enabled=false 이면 전략 실행 건너뜀
        if (!schedulerEnabled) {
            log.debug("[15분 전략] 스케줄러 비활성(scheduler.enabled=false) — 건너뜀");
            return;
        }
        log.info("[15분 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.FIFTEEN_MIN, 15, "15분");
    }

    // ---- 전략 실행 ----

    private void executeStrategy(Candle.CandlePeriod period, int candleUnit, String periodName) {
        // 1. 계정 확인
        Account account;
        try {
            account = accountService.getPrimaryAccount();
        } catch (Exception e) {
            log.error("[{}] 계정 조회 실패 — 전략 중단", periodName, e);
            return;
        }

        if (!Boolean.TRUE.equals(account.getIsActive())) {
            log.warn("[{}] 계정 비활성 상태 — 전략 실행 중단", periodName);
            return;
        }

        // 2. 스캐너로 후보 코인 선별
        List<String> symbols = marketScannerService.scanTopCoins();
        log.info("[{}] 스캐너 선별 코인: {} 개", periodName, symbols.size());

        if (symbols.isEmpty()) {
            log.info("[{}] 후보 코인 없음 — 종료", periodName);
            return;
        }

        // 3. BTC 시장 맥락 사전 계산 (심볼 루프 밖, DB 호출 1회)
        String[] btcCtx = fetchBtcMarketContext(candleUnit);
        String btcRegime = btcCtx[0];
        String btcTrendStr = btcCtx[1];
        String btcReturnStr = btcCtx[2];
        String btcAboveMAStr = btcCtx[3];

        if (marketFilterEnabled && blockWhenMarketDowntrend && "TRENDING_DOWN".equals(btcRegime)) {
            log.info("[{}] BTC 하락장({}) — 전체 롱 진입 차단", periodName, btcRegime);
            return;
        }

        // 4. 각 코인 분석 및 매매 실행
        for (String symbol : symbols) {
            try {
                executeStrategyForSymbol(account, symbol, period, candleUnit, periodName,
                        btcRegime, btcTrendStr, btcReturnStr, btcAboveMAStr);
            } catch (Exception e) {
                log.error("[{}] 전략 실행 실패: {}", periodName, symbol, e);
            }
        }
    }

    /**
     * BTC(또는 market-symbol) 15분봉을 조회하여 시장 맥락 문자열 배열을 반환한다.
     * 반환: [regime, trend(1/0), return(%), aboveMA(1/0)]
     */
    private String[] fetchBtcMarketContext(int candleUnit) {
        try {
            upbitMarketService.getAndSaveCandles(marketSymbol, candleUnit, 50);
            List<Candle> btcCandles = candleRepository
                    .findTopNBySymbolAndPeriodOrderByTimestampDesc(
                            marketSymbol, Candle.CandlePeriod.FIFTEEN_MIN.name(), 50);
            if (btcCandles == null || btcCandles.size() < 26) {
                return new String[]{"UNKNOWN", "0", "0.0", "0"};
            }
            btcCandles.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

            List<Double> closes = btcCandles.stream()
                    .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                    .toList();

            double ema12 = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
            double ema26 = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
            int mktTrend = ema12 > ema26 ? 1 : 0;

            MarketRegime regime = regimeClassifier.classify(btcCandles);

            double mktReturn = 0.0;
            if (closes.size() >= 21) {
                double recent = closes.get(closes.size() - 1);
                double past   = closes.get(closes.size() - 21);
                mktReturn = past > 0 ? (recent - past) / past * 100.0 : 0.0;
            }

            double sma50 = closes.size() >= 50
                    ? TechnicalIndicatorCalculator.calculateSMA(closes, 50) : 0.0;
            int mktAboveMA = sma50 > 0 && closes.get(closes.size() - 1) > sma50 ? 1 : 0;

            return new String[]{regime.name(), String.valueOf(mktTrend),
                    String.valueOf(mktReturn), String.valueOf(mktAboveMA)};
        } catch (Exception e) {
            log.warn("[BTC컨텍스트] 조회 실패 — UNKNOWN 사용: {}", e.getMessage());
            return new String[]{"UNKNOWN", "0", "0.0", "0"};
        }
    }

    private void executeStrategyForSymbol(
            Account account,
            String symbol,
            Candle.CandlePeriod period,
            int candleUnit,
            String periodName,
            String btcRegime,
            String btcTrendStr,
            String btcReturnStr,
            String btcAboveMAStr) {

        // Phase 0: 이 심볼 평가 전체를 추적하는 신호 ID — 조기 종료 포함 모든 경로에서 사용
        String signalId = UUID.randomUUID().toString();
        FeatureSnapshot snap = null;
        AiSignalGate.Decision aiDecision = null;

        // 최신 캔들 및 티커를 업비트 API에서 먼저 fetch해서 DB 갱신
        upbitMarketService.getAndSaveCandles(symbol, candleUnit, 50);
        upbitMarketService.getAndSaveTicker(symbol);

        // MTF 집계용으로 DB에서 더 많은 캔들 로드 (1h EMA26 = 완성된 26시간 = ~104봉)
        List<Candle> allCandles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(symbol, period.name(), 150);

        if (allCandles == null || allCandles.size() < 50) {
            log.warn("[{}] 캔들 데이터 부족: {}, 개수: {}",
                    periodName, symbol, allCandles != null ? allCandles.size() : 0);
            strategyRunLogService.save(signalId, null, null,
                    symbol, periodName, null, null, null, null, null,
                    null, null, null, null, null,
                    "NO_SIGNAL", 0, "캔들 데이터 부족", false, "캔들 데이터 부족");
            return;
        }

        allCandles.sort((c1, c2) -> c1.getTimestamp().compareTo(c2.getTimestamp()));

        // 신호 분석에는 최신 50봉만 사용 (MTF 집계는 allCandles 전체 사용)
        List<Candle> candles = allCandles.size() > 50
                ? allCandles.subList(allCandles.size() - 50, allCandles.size())
                : allCandles;

        List<Double> closePrices = candles.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 26);
        double sma50 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 50);

        TechnicalIndicatorCalculator.MACDValues macdValues =
                TechnicalIndicatorCalculator.calculateMACD(closePrices);
        if (macdValues == null) {
            log.warn("[{}] MACD 계산 불가: {}", periodName, symbol);
            strategyRunLogService.save(signalId, null, null,
                    symbol, periodName, ema12, ema26, sma50, null, null,
                    null, null, null, null, null,
                    "NO_SIGNAL", 0, "MACD 계산 불가", false, "MACD 계산 불가");
            return;
        }

        double rsi = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14);

        // null/0 거래량 캔들 제외 후 volumeMA20 계산 (DB volume 미수집 캔들로 인한 오차 방지)
        List<Double> validVolumes = candles.stream()
                .map(c -> c.getVolume() != null ? c.getVolume().doubleValue() : 0.0)
                .filter(v -> v > 0)
                .toList();
        double volumeMA20 = validVolumes.size() >= 5
                ? TechnicalIndicatorCalculator.calculateVolumeMA(validVolumes, Math.min(20, validVolumes.size()))
                : 0.0;
        // 가장 최신 캔들은 아직 형성 중(불완전)이므로 직전 완성된 캔들의 거래량을 사용.
        // volume이 null이면 최근 유효 캔들로 폴백 (DB 미저장 캔들로 인한 거래량 부족 오판 방지)
        Candle completedCandle = candles.get(candles.size() - 2);
        double currentVolume = 0.0;
        for (int i = candles.size() - 2; i >= Math.max(0, candles.size() - 6); i--) {
            Double v = candles.get(i).getVolume() != null ? candles.get(i).getVolume().doubleValue() : null;
            if (v != null && v > 0) {
                currentVolume = v;
                completedCandle = candles.get(i);
                break;
            }
        }
        double volumeRatio = volumeMA20 > 0 ? currentVolume / volumeMA20 : 0;

        double atrValue = Indicators.atr(candles, 14);
        if (Double.isNaN(atrValue)) atrValue = 0.0;

        MarketRegime regime = regimeClassifier.classify(candles);

        HybridSignalAnalyzer.TrendSignal trend =
                signalAnalyzer.analyzeTrend(ema12, ema26, sma50);
        HybridSignalAnalyzer.MomentumSignal momentum = signalAnalyzer.analyzeMacd(
                macdValues.getMacd(),
                macdValues.getSignalLine(),
                macdValues.getPreviousMacd(),
                macdValues.getPreviousSignalLine());
        HybridSignalAnalyzer.RSISignal rsiSignal = signalAnalyzer.analyzeRSI(rsi);
        HybridSignalAnalyzer.VolumeSignal volumeSignal =
                signalAnalyzer.analyzeVolume(currentVolume, volumeMA20);

        // 완성된 캔들로 패턴 분석 (최신 캔들은 아직 형성 중)
        Candle latest = completedCandle;
        HybridSignalAnalyzer.CandleSignal candleSignal = signalAnalyzer.analyzeCandlePattern(
                latest.getOpenPrice() != null ? latest.getOpenPrice().doubleValue() : 0.0,
                latest.getHighPrice() != null ? latest.getHighPrice().doubleValue() : 0.0,
                latest.getLowPrice() != null ? latest.getLowPrice().doubleValue() : 0.0,
                latest.getClosePrice() != null ? latest.getClosePrice().doubleValue() : 0.0);

        // 연속 2봉 양봉 확인 — 방향성 신뢰도 향상
        if (candles.size() >= 3) {
            Candle prevCandle = candles.get(candles.size() - 3);
            boolean curBullish  = completedCandle.getClosePrice() != null && completedCandle.getOpenPrice() != null
                    && completedCandle.getClosePrice().compareTo(completedCandle.getOpenPrice()) > 0;
            boolean prevBullish = prevCandle.getClosePrice() != null && prevCandle.getOpenPrice() != null
                    && prevCandle.getClosePrice().compareTo(prevCandle.getOpenPrice()) > 0;
            if (curBullish && prevBullish) {
                candleSignal = HybridSignalAnalyzer.CandleSignal.CONSECUTIVE_BULLISH;
            }
        }

        HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                trend, momentum, rsiSignal, volumeSignal, candleSignal);

        // ---- [Phase 0] AI 게이트: 룰 신호 산출 직후, 기존 필터 적용 전 ----
        boolean ruleSaysBuy = tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY;
        snap = FeatureSnapshotFactory.build(
                symbol, periodName, candles,
                ema12, ema26, sma50,
                macdValues.getMacd(), macdValues.getSignalLine(), macdValues.getHistogram(),
                rsi, volumeRatio, atrValue,
                regime, trend, momentum, rsiSignal, volumeSignal, candleSignal, tradeSignal,
                "HYBRID", "COMPOSITE",
                btcRegime,
                Integer.parseInt(btcTrendStr),
                Double.parseDouble(btcReturnStr),
                Integer.parseInt(btcAboveMAStr));
        aiDecision = aiSignalGate.evaluate(ruleSaysBuy, snap);
        // ---- end [Phase 0] ----

        // ---- 레짐 필터: 명확한 횡보장(ADX<20)만 차단. NEUTRAL(ADX 20~25)은 초기 트렌드 포착 허용 ----
        HybridSignalAnalyzer.TradeSignal filteredSignal = tradeSignal;

        // ENSEMBLE 모드에서 AI가 차단한 경우 → NO_SIGNAL로 변환 (기존 필터들도 계속 적용)
        if (!aiDecision.enterAllowed() && ruleSaysBuy) {
            String aiReason = aiDecision.prediction() != null
                    ? String.format("AI 게이트 차단: ML 확률=%.3f", aiDecision.prediction().getBuyProbability())
                    : "AI 게이트 차단 (ML 미사용)";
            filteredSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason(aiReason)
                    .build();
        }
        if (regime == MarketRegime.RANGING
                && (tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                    || tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY)) {
            filteredSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason("레짐 필터: " + regime + " — 신규 매수 차단")
                    .build();
        }

        // ---- 1시간봉 상승추세 필터: 15분봉 집계로 1시간봉 EMA 계산 (train-serve 일관성) ----
        if ((filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY)
                && !isHourlyTrendBullish(allCandles)) {
            filteredSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason("1시간봉 하락추세 — 신규 매수 차단")
                    .build();
        }

        // ---- 4시간봉 상승추세 필터: 15분봉 집계로 4시간봉 EMA 계산 (train-serve 일관성) ----
        if ((filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY)
                && !isFourHourTrendBullish(allCandles)) {
            filteredSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason("4시간봉 하락추세 — 신규 매수 차단")
                    .build();
        }

        log.info("═══════════════════════════════════════");
        log.info("📊 [{}] {}", periodName, symbol);
        log.info("레짐: {} / ATR: {}", regime, String.format("%.4f", atrValue));
        log.info("EMA12: {}, EMA26: {}, SMA50: {}",
                String.format("%.2f", ema12), String.format("%.2f", ema26), String.format("%.2f", sma50));
        log.info("RSI: {}, MACD: {}, Signal: {}",
                String.format("%.1f", rsi), String.format("%.4f", macdValues.getMacd()), String.format("%.4f", macdValues.getSignalLine()));
        log.info("추세: {} / 모멘텀: {} / RSI: {} / 캔들: {}",
                trend, momentum, rsiSignal, candleSignal);
        log.info("원신호: {} / 최종신호: {} / {}%  — {}",
                tradeSignal.getSignal(), filteredSignal.getSignal(),
                filteredSignal.getConfidence(), filteredSignal.getReason());
        log.info("═══════════════════════════════════════");

        Optional<Ticker> tickerOpt = tickerRepository.findBySymbol(symbol);
        if (tickerOpt.isEmpty()) {
            log.warn("[{}] 시세 정보 없음: {}", periodName, symbol);
            strategyRunLogService.save(signalId, snap, aiDecision,
                    symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                    trend, momentum, rsiSignal, volumeSignal, candleSignal,
                    tradeSignal.getSignal().name(), tradeSignal.getConfidence(),
                    tradeSignal.getReason(), false, "시세 정보 없음");
            return;
        }

        BigDecimal currentPrice = tickerOpt.get().getCurrentPrice();

        // EMA 이격 필터: 현재가가 EMA26 대비 3% 초과 이격 시 고점 추격 차단
        if ((filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                || filteredSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY)
                && ema26 > 0
                && currentPrice.doubleValue() > ema26 * 1.03) {
            filteredSignal = HybridSignalAnalyzer.TradeSignal.builder()
                    .signal(HybridSignalAnalyzer.SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason(String.format("EMA 이격 과도 — 현재가 %.0f > EMA26×1.03=%.0f",
                            currentPrice.doubleValue(), ema26 * 1.03))
                    .build();
            log.info("[{}] EMA 이격 필터: {} — {}", periodName, symbol, filteredSignal.getReason());
        }

        boolean orderCreated = executeTradeSignal(account, symbol, currentPrice, filteredSignal, atrValue, signalId);

        strategyRunLogService.save(signalId, snap, aiDecision,
                symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                trend, momentum, rsiSignal, volumeSignal, candleSignal,
                filteredSignal.getSignal().name(), filteredSignal.getConfidence(),
                filteredSignal.getReason(), orderCreated, null);
    }

    // ---- 매매 실행 ----

    private boolean executeTradeSignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            double atrValue,
            String signalId) {

        if (!executionEnabled) {
            log.debug("[{}] 실행 비활성(execution.enabled=false) — 신호 로그만 저장, 주문 없음: {}", symbol, signal.getSignal());
            return false;
        }

        TradeExecutionEngine.TradingParameters params = TradeExecutionEngine.TradingParameters.builder()
                .riskPerTrade(riskPerTrade)
                .maxOrderPercent(maxOrderPercent)
                .minOrderAmount(minOrderAmount)
                .stopLossPercent(stopLossPercent)
                .takeProfitPercent(takeProfitPercent)
                .maxDailyLoss(maxDailyLoss)
                .useStopLoss(true)
                .useTakeProfit(true)
                .maxOpenPositions(maxOpenPositions)
                .build();

        return switch (signal.getSignal()) {
            case STRONG_BUY, BUY -> executeBuySignal(account, symbol, currentPrice, signal, params, atrValue, signalId);
            // SELL은 15분봉 노이즈가 많아 즉시 청산하지 않음 — trailing stop이 포지션 관리
            // STRONG_SELL(bearishScore≥4 + VERY_HIGH volume)만 즉시 청산
            case STRONG_SELL -> executeSellSignal(account, symbol, currentPrice, signalId);
            default -> false;
        };
    }

    private boolean executeBuySignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradeExecutionEngine.TradingParameters params,
            double atrValue,
            String signalId) {

        try {
            RiskService.RiskCheckResult riskResult = riskService.validateBuy(
                    account, symbol, maxDailyLoss, maxOpenPositions);

            if (!riskResult.allowed()) {
                log.info("[{}] 매수 차단: {}", symbol, riskResult.reason());
                return false;
            }

            Order order = executionEngine.executeBySignal(
                    account, symbol, currentPrice, signal, params);

            if (order == null) return false;

            // 업비트에 실제 주문 제출
            String ordType = order.getType() == Order.OrderType.MARKET ? "price" : "limit";
            UpbitOrderDto upbitOrder = upbitApiClient.createOrder(
                    symbol, "bid", ordType, order.getQuantity(), order.getPrice());

            if (upbitOrder == null) {
                log.warn("[{}] 업비트 매수 주문 제출 실패 — DB 저장 생략", symbol);
                return false;
            }

            order.setExchangeOrderId(upbitOrder.getUuid());
            BigDecimal filledQty = order.getQuantity();
            if ("done".equals(upbitOrder.getState())) {
                order.setStatus(Order.OrderStatus.FILLED);
                if (upbitOrder.getExecutedVolume() != null) {
                    filledQty = upbitOrder.getExecutedVolume();
                }
                order.setFilledQuantity(filledQty);
            }

            orderService.createOrder(order);
            log.info("✓ 매수 주문 생성: {}, 수량: {}, UUID: {}", symbol, order.getQuantity(), upbitOrder.getUuid());

            // Position 생성 및 WebSocket 모니터링 등록
            double price = currentPrice.doubleValue();
            // ATR 기반 손절/목표가 (변동성에 맞는 동적 설정)
            // ATR 없으면 고정 비율 폴백
            double stopLoss, takeProfit;
            if (atrValue > 0) {
                double stopDist = stopAtrMult * atrValue;
                stopLoss   = price - stopDist;
                takeProfit = takeProfitR > 0 ? price + takeProfitR * stopDist : price * (1.0 + takeProfitPercent.doubleValue() / 100.0);
                // 안전 한도: 손절 최대 10%, 최소 2.0%
                double stopPct = (price - stopLoss) / price;
                if (stopPct > 0.10 || stopPct < 0.02) stopLoss = price * (1.0 - stopLossPercent.doubleValue() / 100.0);
            } else {
                stopLoss   = price * (1.0 - stopLossPercent.doubleValue() / 100.0);
                takeProfit = price * (1.0 + takeProfitPercent.doubleValue() / 100.0);
            }

            Position position = Position.builder()
                    .account(account)
                    .symbol(symbol)
                    .quantity(filledQty)
                    .avgBuyPrice(currentPrice)
                    .currentPrice(currentPrice)
                    .initialStopLoss(stopLoss)
                    .currentStopLoss(stopLoss)
                    .takeProfitPrice(takeProfit)
                    .highestPriceSinceEntry(price)
                    .atrAtEntry(atrValue)
                    .signalId(signalId)  // Phase 0: 청산 시 trade_history로 전달
                    .status(Position.PositionStatus.OPEN)
                    .build();

            Position savedPosition = positionRepository.save(position);
            positionMonitor.trackPosition(savedPosition);
            log.info("✓ 포지션 등록 및 모니터링 시작: {}, 손절={}, 익절={}",
                    symbol,
                    String.format("%.2f", stopLoss),
                    String.format("%.2f", takeProfit));
            return true;

        } catch (Exception e) {
            log.error("[{}] 매수 주문 생성 실패", symbol, e);
        }
        return false;
    }

    private boolean executeSellSignal(Account account, String symbol, BigDecimal currentPrice, String signalId) {
        try {
            Optional<Position> positionOpt = positionRepository
                    .findActiveByAccountAndSymbol(account, symbol);

            if (positionOpt.isEmpty()) return false;

            Position position = positionOpt.get();
            Order order = executionEngine.executeSellSignal(
                    account, symbol, currentPrice, position.getQuantity(), null);

            if (order == null) return false;

            // 업비트에 실제 주문 제출 — 시장가 매도는 price 불필요
            boolean isMarket = order.getType() == Order.OrderType.MARKET;
            String ordType = isMarket ? "market" : "limit";
            BigDecimal sellPrice = isMarket ? null : order.getPrice();
            UpbitOrderDto upbitOrder = upbitApiClient.createOrder(
                    symbol, "ask", ordType, order.getQuantity(), sellPrice);

            if (upbitOrder == null) {
                log.warn("[{}] 업비트 매도 주문 제출 실패 — DB 저장 생략", symbol);
                return false;
            }

            order.setExchangeOrderId(upbitOrder.getUuid());
            if ("done".equals(upbitOrder.getState())) {
                order.setStatus(Order.OrderStatus.FILLED);
                order.setFilledQuantity(upbitOrder.getExecutedVolume());
            }

            orderService.createOrder(order);
            log.info("✓ 매도 주문 생성: {}, 수량: {}, UUID: {}", symbol, position.getQuantity(), upbitOrder.getUuid());

            // 거래 기록 저장 — 원래 매수 신호 ID를 position에서 읽어 연결
            tradeHistoryService.record(
                    account,
                    symbol,
                    position.getAvgBuyPrice(),
                    currentPrice,
                    position.getQuantity(),
                    position.getCreatedAt(),
                    TradeHistory.ExitType.SELL_SIGNAL,
                    "전략 매도 신호",
                    position.getAtrAtEntry(),
                    position.getHighestPriceSinceEntry(),
                    false,
                    position.getSignalId());  // Phase 0: 원래 매수 신호 ID

            return true;

        } catch (Exception e) {
            log.error("[{}] 매도 주문 생성 실패", symbol, e);
        }
        return false;
    }

    // ---- 1시간봉 추세 확인 ----

    /**
     * 15분봉 집계로 1시간봉 EMA12 > EMA26 여부 판단 (train-serve 일관성).
     * 완성된 1시간봉 26개 미만이면 true를 반환하여 매수를 허용한다.
     */
    private boolean isHourlyTrendBullish(List<Candle> fifteenMinCandles) {
        List<double[]> h1Agg = aggregateToHigherTf(fifteenMinCandles, 60);
        if (h1Agg.size() < 26) {
            log.debug("1시간봉(15m집계) 데이터 부족({}) — 필터 스킵", h1Agg.size());
            return true;
        }
        List<Double> closes = h1Agg.stream().map(b -> b[0]).toList();
        double ema12h = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
        double ema26h = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
        boolean bullish = ema12h > ema26h;
        log.debug("1시간봉(15m집계) EMA12={}, EMA26={} → {}",
                String.format("%.4f", ema12h), String.format("%.4f", ema26h),
                bullish ? "상승추세" : "하락추세");
        return bullish;
    }

    // ---- 4시간봉 추세 확인 ----

    /**
     * 15분봉 집계로 4시간봉 EMA12 > EMA26 여부 판단 (train-serve 일관성).
     * 완성된 4시간봉 26개 미만이면 true를 반환하여 매수를 허용한다.
     */
    private boolean isFourHourTrendBullish(List<Candle> fifteenMinCandles) {
        List<double[]> h4Agg = aggregateToHigherTf(fifteenMinCandles, 240);
        if (h4Agg.size() < 26) {
            log.debug("4시간봉(15m집계) 데이터 부족({}) — 필터 스킵", h4Agg.size());
            return true;
        }
        List<Double> closes = h4Agg.stream().map(b -> b[0]).toList();
        double ema12h4 = TechnicalIndicatorCalculator.calculateEMA(closes, 12);
        double ema26h4 = TechnicalIndicatorCalculator.calculateEMA(closes, 26);
        boolean bullish = ema12h4 > ema26h4;
        log.debug("4시간봉(15m집계) EMA12={}, EMA26={} → {}",
                String.format("%.4f", ema12h4), String.format("%.4f", ema26h4),
                bullish ? "상승추세" : "하락추세");
        return bullish;
    }

    /**
     * 15분봉을 시간 경계 기준으로 상위 타임프레임 봉으로 집계.
     * 진행 중인 마지막 봉은 포함하지 않아 look-ahead 편향 방지.
     * 반환: {close, unusedIdx} 배열 — close만 EMA 계산에 사용.
     */
    private static List<double[]> aggregateToHigherTf(List<Candle> candles, int periodMinutes) {
        List<double[]> result = new ArrayList<>();
        long bucketSec = periodMinutes * 60L;
        long currentBucket = -1;
        double currentClose = 0.0;

        for (Candle c : candles) {
            if (c.getTimestamp() == null) continue;
            long epochSec = c.getTimestamp().toEpochSecond(ZoneOffset.UTC);
            long bucket = epochSec / bucketSec;

            if (bucket != currentBucket) {
                if (currentBucket >= 0) {
                    result.add(new double[]{currentClose, 0});
                }
                currentBucket = bucket;
            }
            currentClose = c.getClosePrice() != null ? c.getClosePrice().doubleValue() : currentClose;
        }
        // 마지막 진행 중인 봉 미포함 (완성된 봉만 사용)
        return result;
    }

}
