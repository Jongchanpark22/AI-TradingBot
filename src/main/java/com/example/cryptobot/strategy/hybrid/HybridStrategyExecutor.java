package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderService;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.risk.RiskService;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategyRepository;
import com.example.cryptobot.strategy.core.StrategyRunLog;
import com.example.cryptobot.strategy.core.StrategyRunLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final StrategyRepository strategyRepository;
    private final PositionRepository positionRepository;
    private final OrderService orderService;
    private final RiskService riskService;
    private final StrategyRunLogRepository strategyRunLogRepository;

    private final HybridSignalAnalyzer signalAnalyzer = new HybridSignalAnalyzer();
    private final TradeExecutionEngine executionEngine = new TradeExecutionEngine();

    @Scheduled(cron = "10 0 * * * *")
    public void executeHybridStrategy1Hour() {
        log.info("[1시간 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.ONE_HOUR, "1시간");
    }

    @Scheduled(cron = "10 0 0,4,8,12,16,20 * * *")
    public void executeHybridStrategy4Hour() {
        log.info("[4시간 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.FOUR_HOUR, "4시간");
    }

    private void executeStrategy(Candle.CandlePeriod period, String periodName) {
        try {
            List<Strategy> activeStrategies = strategyRepository
                    .findByStatusAndTargetPeriodAndAccount_IsActive(
                            Strategy.StrategyStatus.ACTIVE,
                            period,
                            true
                    );

            log.info("[{}] 활성 전략: {} 개", periodName, activeStrategies.size());

            for (Strategy strategy : activeStrategies) {
                try {
                    executeStrategyForSymbol(strategy, strategy.getSymbol(), period, periodName);
                } catch (Exception e) {
                    log.error("전략 실행 실패: {}, 심볼: {}", strategy.getName(), strategy.getSymbol(), e);
                }
            }

        } catch (Exception e) {
            log.error("[{}] 전략 실행 중 오류", periodName, e);
        }
    }

    private void executeStrategyForSymbol(
            Strategy strategy,
            String symbol,
            Candle.CandlePeriod period,
            String periodName) {

        List<Candle> candles = candleRepository.findTopNBySymbolAndPeriodOrderByTimestampDesc(symbol, period, 50);

        if (candles == null || candles.size() < 50) {
            log.warn("캔들 데이터 부족: {}, 개수: {}", symbol, candles != null ? candles.size() : 0);
            saveRunLog(strategy, symbol, periodName, null, null, null, null, null, "NO_SIGNAL", 0, "캔들 데이터 부족", false, "캔들 데이터 부족");
            return;
        }

        candles.sort((c1, c2) -> c1.getTimestamp().compareTo(c2.getTimestamp()));

        // BigDecimal을 Double로 변환
        List<Double> closePrices = candles.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 26);
        double sma50 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 50);

        TechnicalIndicatorCalculator.MACDValues macdValues = TechnicalIndicatorCalculator.calculateMACD(closePrices);
        if (macdValues == null) {
            log.warn("MACD 계산 불가: {}", symbol);
            saveRunLog(strategy, symbol, periodName, ema12, ema26, sma50, null, null, "NO_SIGNAL", 0, "MACD 계산 불가", false, "MACD 계산 불가");
            return;
        }

        double rsi = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14);

        // BigDecimal을 Double로 변환
        List<Double> volumes = candles.stream()
                .map(c -> c.getVolume() != null ? c.getVolume().doubleValue() : 0.0)
                .toList();

        double volumeMA20 = TechnicalIndicatorCalculator.calculateVolumeMA(volumes, 20);
        double currentVolume = candles.get(candles.size() - 1).getVolume() != null 
                ? candles.get(candles.size() - 1).getVolume().doubleValue() : 0.0;
        double volumeRatio = volumeMA20 > 0 ? currentVolume / volumeMA20 : 0;

        HybridSignalAnalyzer.TrendSignal trend = signalAnalyzer.analyzeTrend(ema12, ema26, sma50);
        HybridSignalAnalyzer.MomentumSignal momentum = signalAnalyzer.analyzeMacd(
                macdValues.getMacd(),
                macdValues.getSignalLine(),
                macdValues.getPreviousMacd(),
                macdValues.getPreviousSignalLine()
        );
        HybridSignalAnalyzer.RSISignal rsiSignal = signalAnalyzer.analyzeRSI(rsi);
        HybridSignalAnalyzer.VolumeSignal volumeSignal = signalAnalyzer.analyzeVolume(currentVolume, volumeMA20);

        Candle latest = candles.get(candles.size() - 1);
        HybridSignalAnalyzer.CandleSignal candleSignal = signalAnalyzer.analyzeCandlePattern(
                latest.getOpenPrice() != null ? latest.getOpenPrice().doubleValue() : 0.0,
                latest.getHighPrice() != null ? latest.getHighPrice().doubleValue() : 0.0,
                latest.getLowPrice() != null ? latest.getLowPrice().doubleValue() : 0.0,
                latest.getClosePrice() != null ? latest.getClosePrice().doubleValue() : 0.0
        );

        HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                trend, momentum, rsiSignal, volumeSignal, candleSignal
        );

        log.info("═══════════════════════════════════════");
        log.info("📊 [{}] {} - {}", periodName, symbol, strategy.getName());
        log.info("EMA12: {}, EMA26: {}, SMA50: {}", ema12, ema26, sma50);
        log.info("RSI: {}, MACD: {}, Signal: {}", rsi, macdValues.getMacd(), macdValues.getSignalLine());
        log.info("Trade Signal: {} / {}%", tradeSignal.getSignal(), tradeSignal.getConfidence());
        log.info("Reason: {}", tradeSignal.getReason());
        log.info("═══════════════════════════════════════");

        Optional<Ticker> tickerOpt = tickerRepository.findBySymbol(symbol);
        if (tickerOpt.isEmpty()) {
            log.warn("시세 정보 없음: {}", symbol);
            saveRunLog(strategy, symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                    tradeSignal.getSignal().name(), tradeSignal.getConfidence(), tradeSignal.getReason(), false, "시세 정보 없음");
            return;
        }

        BigDecimal currentPrice = tickerOpt.get().getCurrentPrice();
        boolean orderCreated = executeTradeSignal(strategy, symbol, currentPrice, tradeSignal);

        saveRunLog(strategy, symbol, periodName, ema12, ema26, sma50, rsi, volumeRatio,
                tradeSignal.getSignal().name(), tradeSignal.getConfidence(), tradeSignal.getReason(), orderCreated, null);
    }

    private boolean executeTradeSignal(
            Strategy strategy,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal) {

        Account account = strategy.getAccount();

        TradeExecutionEngine.TradingParameters params = TradeExecutionEngine.TradingParameters.builder()
                .riskPerTrade(new BigDecimal("0.03"))
                .stopLossPercent(strategy.getStopLossPercent() != null ? strategy.getStopLossPercent() : new BigDecimal("2.5"))
                .takeProfitPercent(strategy.getTakeProfitPercent() != null ? strategy.getTakeProfitPercent() : new BigDecimal("6.75"))
                .maxDailyLoss(strategy.getMaxDailyLoss() != null ? strategy.getMaxDailyLoss() : new BigDecimal("10.0"))
                .useStopLoss(Boolean.TRUE.equals(strategy.getUseStopLoss()))
                .useTakeProfit(Boolean.TRUE.equals(strategy.getUseTakeProfit()))
                .maxOpenPositions(3)
                .build();

        switch (signal.getSignal()) {
            case STRONG_BUY, BUY -> {
                return executeBuySignal(strategy, symbol, currentPrice, signal, params);
            }
            case STRONG_SELL, SELL -> {
                return executeSellSignal(strategy, symbol, currentPrice);
            }
            case NO_SIGNAL -> {
                log.debug("신호 없음: {}", symbol);
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean executeBuySignal(
            Strategy strategy,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradeExecutionEngine.TradingParameters params) {

        try {
            RiskService.RiskCheckResult riskResult = riskService.validateBuy(
                    strategy.getAccount(),
                    symbol,
                    strategy.getMaxDailyLoss(),
                    params.getMaxOpenPositions()
            );

            if (!riskResult.allowed()) {
                log.info("매수 차단: {}", riskResult.reason());
                return false;
            }

            Order order = executionEngine.executeBySignal(
                    strategy.getAccount(),
                    symbol,
                    currentPrice,
                    signal,
                    params
            );

            if (order != null) {
                orderService.createOrder(order);
                log.info("✓ 매수 주문 생성: {}, 수량: {}", symbol, order.getQuantity());
                return true;
            }

        } catch (Exception e) {
            log.error("매수 주문 생성 실패", e);
        }

        return false;
    }

    private boolean executeSellSignal(
            Strategy strategy,
            String symbol,
            BigDecimal currentPrice) {

        try {
            Optional<Position> positionOpt = positionRepository
                    .findByAccountAndSymbolAndStatus(
                            strategy.getAccount(),
                            symbol,
                            Position.PositionStatus.OPEN
                    );

            if (positionOpt.isPresent()) {
                Position position = positionOpt.get();

                Order order = executionEngine.executeSellSignal(
                        strategy.getAccount(),
                        symbol,
                        currentPrice,
                        position.getQuantity(),
                        null
                );

                orderService.createOrder(order);
                log.info("✓ 매도 주문 생성: {}, 수량: {}", symbol, position.getQuantity());
                return true;
            }

        } catch (Exception e) {
            log.error("매도 주문 생성 실패", e);
        }

        return false;
    }

    private void saveRunLog(
            Strategy strategy,
            String symbol,
            String periodName,
            Double ema12,
            Double ema26,
            Double sma50,
            Double rsi,
            Double volumeRatio,
            String finalSignal,
            Integer confidence,
            String reason,
            boolean orderCreated,
            String blockedReason) {

        StrategyRunLog logEntity = StrategyRunLog.builder()
                .strategyId(strategy.getId())
                .strategyName(strategy.getName())
                .symbol(symbol)
                .period(periodName)
                .ema12(ema12)
                .ema26(ema26)
                .sma50(sma50)
                .rsi(rsi)
                .volumeRatio(volumeRatio)
                .finalSignal(finalSignal)
                .confidence(confidence)
                .reason(reason)
                .orderCreated(orderCreated)
                .blockedReason(blockedReason)
                .build();

        strategyRunLogRepository.save(logEntity);
    }
}