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
import com.example.cryptobot.strategy.core.StrategyRunLog;
import com.example.cryptobot.strategy.core.StrategyRunLogRepository;
import com.example.cryptobot.strategy.scanner.MarketScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridStrategyExecutor {

    private final CandleRepository candleRepository;
    private final TickerRepository tickerRepository;
    private final PositionRepository positionRepository;
    private final OrderService orderService;
    private final RiskService riskService;
    private final StrategyRunLogRepository strategyRunLogRepository;
    private final MarketScannerService marketScannerService;
    private final AccountService accountService;
    private final UpbitMarketService upbitMarketService;
    private final UpbitApiClient upbitApiClient;

    // ---- 리스크 파라미터 (application.yml에서 설정) ----
    @Value("${trading.risk.stop-loss-percent:2.5}")
    private BigDecimal stopLossPercent;

    @Value("${trading.risk.take-profit-percent:6.75}")
    private BigDecimal takeProfitPercent;

    @Value("${trading.risk.max-daily-loss:10.0}")
    private BigDecimal maxDailyLoss;

    @Value("${trading.risk.risk-per-trade:0.03}")
    private BigDecimal riskPerTrade;

    @Value("${trading.risk.max-open-positions:3}")
    private int maxOpenPositions;

    private final HybridSignalAnalyzer signalAnalyzer = new HybridSignalAnalyzer();
    private final TradeExecutionEngine executionEngine = new TradeExecutionEngine();

    // ---- 스케줄러 ----

    /** 5분마다 실행 — 최신 5분봉 50개 기준으로 신호 분석 */
    @Scheduled(cron = "0 0/5 * * * *")
    public void executeHybridStrategy5Min() {
        log.info("[5분 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.FIVE_MIN, 5, "5분");
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

        // 3. 각 코인 분석 및 매매 실행
        for (String symbol : symbols) {
            try {
                executeStrategyForSymbol(account, symbol, period, candleUnit, periodName);
            } catch (Exception e) {
                log.error("[{}] 전략 실행 실패: {}", periodName, symbol, e);
            }
        }
    }

    private void executeStrategyForSymbol(
            Account account,
            String symbol,
            Candle.CandlePeriod period,
            int candleUnit,
            String periodName) {

        // 최신 캔들 및 티커를 업비트 API에서 먼저 fetch해서 DB 갱신
        upbitMarketService.getAndSaveCandles(symbol, candleUnit, 50);
        upbitMarketService.getAndSaveTicker(symbol);

        List<Candle> candles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(symbol, period.name(), 50);

        if (candles == null || candles.size() < 50) {
            log.warn("[{}] 캔들 데이터 부족: {}, 개수: {}",
                    periodName, symbol, candles != null ? candles.size() : 0);
            saveRunLog(symbol, periodName, null, null, null, null, null,
                    null, null, null, null, null,
                    "NO_SIGNAL", 0, "캔들 데이터 부족", false, "캔들 데이터 부족");
            return;
        }

        candles.sort((c1, c2) -> c1.getTimestamp().compareTo(c2.getTimestamp()));

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
            saveRunLog(symbol, periodName, ema12, ema26, sma50, null, null,
                    null, null, null, null, null,
                    "NO_SIGNAL", 0, "MACD 계산 불가", false, "MACD 계산 불가");
            return;
        }

        double rsi = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14);

        List<Double> volumes = candles.stream()
                .map(c -> c.getVolume() != null ? c.getVolume().doubleValue() : 0.0)
                .toList();

        double volumeMA20 = TechnicalIndicatorCalculator.calculateVolumeMA(volumes, 20);
        double currentVolume = candles.get(candles.size() - 1).getVolume() != null
                ? candles.get(candles.size() - 1).getVolume().doubleValue() : 0.0;
        double volumeRatio = volumeMA20 > 0 ? currentVolume / volumeMA20 : 0;

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

        Candle latest = candles.get(candles.size() - 1);
        HybridSignalAnalyzer.CandleSignal candleSignal = signalAnalyzer.analyzeCandlePattern(
                latest.getOpenPrice() != null ? latest.getOpenPrice().doubleValue() : 0.0,
                latest.getHighPrice() != null ? latest.getHighPrice().doubleValue() : 0.0,
                latest.getLowPrice() != null ? latest.getLowPrice().doubleValue() : 0.0,
                latest.getClosePrice() != null ? latest.getClosePrice().doubleValue() : 0.0);

        HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                trend, momentum, rsiSignal, volumeSignal, candleSignal);

        log.info("═══════════════════════════════════════");
        log.info("📊 [{}] {}", periodName, symbol);
        log.info("EMA12: {}, EMA26: {}, SMA50: {}",
                String.format("%.2f", ema12), String.format("%.2f", ema26), String.format("%.2f", sma50));
        log.info("RSI: {}, MACD: {}, Signal: {}",
                String.format("%.1f", rsi), String.format("%.4f", macdValues.getMacd()), String.format("%.4f", macdValues.getSignalLine()));
        log.info("추세: {} / 모멘텀: {} / RSI: {} / 캔들: {}",
                trend, momentum, rsiSignal, candleSignal);
        log.info("최종 신호: {} / {}%  — {}", tradeSignal.getSignal(), tradeSignal.getConfidence(), tradeSignal.getReason());
        log.info("═══════════════════════════════════════");

        Optional<Ticker> tickerOpt = tickerRepository.findBySymbol(symbol);
        if (tickerOpt.isEmpty()) {
            log.warn("[{}] 시세 정보 없음: {}", periodName, symbol);
            saveRunLog(symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                    trend, momentum, rsiSignal, volumeSignal, candleSignal,
                    tradeSignal.getSignal().name(), tradeSignal.getConfidence(),
                    tradeSignal.getReason(), false, "시세 정보 없음");
            return;
        }

        BigDecimal currentPrice = tickerOpt.get().getCurrentPrice();
        boolean orderCreated = executeTradeSignal(account, symbol, currentPrice, tradeSignal);

        saveRunLog(symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                trend, momentum, rsiSignal, volumeSignal, candleSignal,
                tradeSignal.getSignal().name(), tradeSignal.getConfidence(),
                tradeSignal.getReason(), orderCreated, null);
    }

    // ---- 매매 실행 ----

    private boolean executeTradeSignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal) {

        TradeExecutionEngine.TradingParameters params = TradeExecutionEngine.TradingParameters.builder()
                .riskPerTrade(riskPerTrade)
                .stopLossPercent(stopLossPercent)
                .takeProfitPercent(takeProfitPercent)
                .maxDailyLoss(maxDailyLoss)
                .useStopLoss(true)
                .useTakeProfit(true)
                .maxOpenPositions(maxOpenPositions)
                .build();

        return switch (signal.getSignal()) {
            case STRONG_BUY, BUY -> executeBuySignal(account, symbol, currentPrice, signal, params);
            case STRONG_SELL, SELL -> executeSellSignal(account, symbol, currentPrice);
            default -> false;
        };
    }

    private boolean executeBuySignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradeExecutionEngine.TradingParameters params) {

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
            if ("done".equals(upbitOrder.getState())) {
                order.setStatus(Order.OrderStatus.FILLED);
                order.setFilledQuantity(upbitOrder.getExecutedVolume());
            }

            orderService.createOrder(order);
            log.info("✓ 매수 주문 생성: {}, 수량: {}, UUID: {}", symbol, order.getQuantity(), upbitOrder.getUuid());
            return true;

        } catch (Exception e) {
            log.error("[{}] 매수 주문 생성 실패", symbol, e);
        }
        return false;
    }

    private boolean executeSellSignal(Account account, String symbol, BigDecimal currentPrice) {
        try {
            Optional<Position> positionOpt = positionRepository
                    .findByAccountAndSymbolAndStatus(account, symbol, Position.PositionStatus.OPEN);

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
            return true;

        } catch (Exception e) {
            log.error("[{}] 매도 주문 생성 실패", symbol, e);
        }
        return false;
    }

    // ---- 로그 저장 ----

    private void saveRunLog(
            String symbol,
            String periodName,
            Double ema12,
            Double ema26,
            Double sma50,
            Double rsi,
            Double volumeRatio,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSignal,
            HybridSignalAnalyzer.VolumeSignal volumeSignal,
            HybridSignalAnalyzer.CandleSignal candleSignal,
            String finalSignal,
            Integer confidence,
            String reason,
            boolean orderCreated,
            String blockedReason) {

        StrategyRunLog logEntity = StrategyRunLog.builder()
                .strategyId(null)
                .strategyName("AUTO_SCANNER")
                .symbol(symbol)
                .period(periodName)
                .ema12(ema12)
                .ema26(ema26)
                .sma50(sma50)
                .rsi(rsi)
                .volumeRatio(volumeRatio)
                .trendSignal(trend != null ? trend.name() : "UNKNOWN")
                .momentumSignal(momentum != null ? momentum.name() : "UNKNOWN")
                .rsiSignal(rsiSignal != null ? rsiSignal.name() : "UNKNOWN")
                .volumeSignal(volumeSignal != null ? volumeSignal.name() : "UNKNOWN")
                .candleSignal(candleSignal != null ? candleSignal.name() : "UNKNOWN")
                .finalSignal(finalSignal)
                .confidence(confidence)
                .reason(reason)
                .orderCreated(orderCreated)
                .blockedReason(blockedReason)
                .build();

        strategyRunLogRepository.save(logEntity);
    }
}
