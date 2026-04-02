package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.order.OrderService;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 하이브리드 거래 전략 실행 서비스
 * 
 * 1시간 또는 4시간 주기로 실행:
 * 1. 캔들 데이터 수집
 * 2. 기술적 지표 계산
 * 3. 매매 신호 생성
 * 4. 주문 실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridStrategyExecutor {

    private final CandleRepository candleRepository;
    private final TickerRepository tickerRepository;
    private final StrategyRepository strategyRepository;
    private final PositionRepository positionRepository;
    private final OrderService orderService;

    private final HybridSignalAnalyzer signalAnalyzer = new HybridSignalAnalyzer();
    private final TradeExecutionEngine executionEngine = new TradeExecutionEngine();

    /**
     * 1시간 주기 전략 실행
     * (업비트에서 가장 변동성 높은 시간대: 오전 9시, 오후 3시-9시)
     */
    @Scheduled(cron = "0 0 * * * *")  // 매 시간 정각
    public void executeHybridStrategy1Hour() {
        log.info("[1시간 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.ONE_HOUR, "1시간");
    }

    /**
     * 4시간 주기 전략 실행
     * (좀 더 장기적인 추세 포착)
     */
    @Scheduled(cron = "0 0 0,4,8,12,16,20 * * *")  // 4시간마다
    public void executeHybridStrategy4Hour() {
        log.info("[4시간 전략] 실행 시작");
        executeStrategy(Candle.CandlePeriod.FOUR_HOUR, "4시간");
    }

    /**
     * 전략 실행 메인 로직
     */
    private void executeStrategy(Candle.CandlePeriod period, String periodName) {
        try {
            // 활성 전략 조회
            List<Strategy> activeStrategies = strategyRepository
                    .findByStatusAndAccount_IsActive(
                            Strategy.StrategyStatus.ACTIVE,
                            true);
            
            log.info("[{}] 활성 전략: {} 개", periodName, activeStrategies.size());
            
            for (Strategy strategy : activeStrategies) {
                try {
                    executeStrategyForSymbol(
                            strategy,
                            strategy.getSymbol(),
                            period,
                            periodName);
                } catch (Exception e) {
                    log.error("전략 실행 실패: {}, 심볼: {}", strategy.getName(), strategy.getSymbol(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("[{}] 전략 실행 중 오류", periodName, e);
        }
    }

    /**
     * 개별 심볼에 대한 전략 실행
     */
    private void executeStrategyForSymbol(
            Strategy strategy,
            String symbol,
            Candle.CandlePeriod period,
            String periodName) {
        
        // 1. 캔들 데이터 수집 (최근 50개)
        List<Candle> candles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(symbol, period, 50);
        
        if (candles == null || candles.size() < 26) {
            log.warn("캔들 데이터 부족: {}, 개수: {}", symbol, candles != null ? candles.size() : 0);
            return;
        }
        
        // 역순으로 정렬 (오래된 순)
        candles.sort((c1, c2) -> c1.getTimestamp().compareTo(c2.getTimestamp()));
        
        // 2. 종가 추출
        List<Double> closePrices = candles.stream()
                .map(Candle::getClosePrice)
                .toList();
        
        // 3. 기술적 지표 계산
        double ema12 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 12);
        double ema26 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 26);
        double sma50 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 50);
        
        TechnicalIndicatorCalculator.MACDValues macdValues = 
                TechnicalIndicatorCalculator.calculateMACD(closePrices);
        
        double rsi = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14);
        
        // 거래량 분석
        List<Double> volumes = candles.stream()
                .map(Candle::getVolume)
                .toList();
        double volumeMA20 = TechnicalIndicatorCalculator.calculateVolumeMA(volumes, 20);
        double currentVolume = candles.get(candles.size() - 1).getVolume();
        
        // 4. 신호 분석
        HybridSignalAnalyzer.TrendSignal trend = signalAnalyzer.analyzeTrend(ema12, ema26, sma50);
        HybridSignalAnalyzer.MomentumSignal momentum = signalAnalyzer.analyzeMacd(
                macdValues.getMacd(), macdValues.getSignalLine(), 0);
        HybridSignalAnalyzer.RSISignal rsiSignal = signalAnalyzer.analyzeRSI(rsi);
        HybridSignalAnalyzer.VolumeSignal volume = signalAnalyzer.analyzeVolume(currentVolume, volumeMA20);
        HybridSignalAnalyzer.CandleSignal candle = signalAnalyzer.analyzeCandlePattern(
                candles.get(candles.size() - 1).getOpenPrice(),
                candles.get(candles.size() - 1).getHighPrice(),
                candles.get(candles.size() - 1).getLowPrice(),
                candles.get(candles.size() - 1).getClosePrice());
        
        // 5. 최종 거래 신호 생성
        HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                trend, momentum, rsiSignal, volume, candle);
        
        log.info("═══════════════════════════════════════");
        log.info("📊 [{}] {} - {}", periodName, symbol, strategy.getName());
        log.info("─────────────────────────────────────");
        log.info("📈 기술 지표:");
        log.info("   EMA(12): {}", String.format("%.2f", ema12));
        log.info("   EMA(26): {}", String.format("%.2f", ema26));
        log.info("   SMA(50): {}", String.format("%.2f", sma50));
        log.info("   RSI(14): {}", String.format("%.2f", rsi));
        log.info("   MACD: {}", String.format("%.4f", macdValues.getMacd()));
        log.info("─────────────────────────────────────");
        log.info("🎯 신호:");
        log.info("   트렌드: {}", trend);
        log.info("   모멘텀: {}", momentum);
        log.info("   거래량: {} ({}%)", volume, 
                String.format("%.0f", (currentVolume / volumeMA20) * 100));
        log.info("   캔들: {}", candle);
        log.info("─────────────────────────────────────");
        log.info("💡 거래 신호: {} (신뢰도: {}%)", 
                tradeSignal.getSignal(), tradeSignal.getConfidence());
        log.info("   이유: {}", tradeSignal.getReason());
        log.info("═══════════════════════════════════════");
        
        // 6. 현재 가격 조회
        Optional<Ticker> tickerOpt = tickerRepository.findBySymbol(symbol);
        if (tickerOpt.isEmpty()) {
            log.warn("시세 정보 없음: {}", symbol);
            return;
        }
        
        double currentPrice = tickerOpt.get().getCurrentPrice();
        
        // 7. 거래 신호에 따른 주문 실행
        executeTradeSignal(strategy, symbol, currentPrice, tradeSignal);
    }

    /**
     * 거래 신호에 따른 주문 실행
     */
    private void executeTradeSignal(
            Strategy strategy,
            String symbol,
            double currentPrice,
            HybridSignalAnalyzer.TradeSignal signal) {
        
        Account account = strategy.getAccount();
        TradeExecutionEngine.TradingParameters params = TradeExecutionEngine.TradingParameters.builder()
                .riskPerTrade(0.03)
                .stopLossPercent(strategy.getStopLossPercent())
                .takeProfitPercent(strategy.getTakeProfitPercent())
                .maxDailyLoss(strategy.getMaxDailyLoss())
                .useStopLoss(strategy.getUseStopLoss())
                .useTakeProfit(strategy.getUseTakeProfit())
                .build();
        
        switch (signal.getSignal()) {
            case STRONG_BUY:
            case BUY:
                log.info("🟢 [매수] {} - {}", strategy.getName(), symbol);
                executeBySignal(strategy, symbol, currentPrice, signal, params);
                break;
                
            case STRONG_SELL:
            case SELL:
                log.info("🔴 [매도] {} - {}", strategy.getName(), symbol);
                executeSellSignal(strategy, symbol, currentPrice);
                break;
                
            case NO_SIGNAL:
                log.debug("⚪ [신호없음] {} - {}", strategy.getName(), symbol);
                break;
                
            default:
                break;
        }
    }

    /**
     * 매수 신호 실행
     */
    private void executeBySignal(
            Strategy strategy,
            String symbol,
            double currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradeExecutionEngine.TradingParameters params) {
        
        try {
            var order = executionEngine.executeBySignal(
                    strategy.getAccount(),
                    symbol,
                    currentPrice,
                    signal,
                    params);
            
            if (order != null) {
                orderService.createOrder(order);
                log.info("✓ 매수 주문 생성: {}, 수량: {}", symbol, order.getQuantity());
            }
        } catch (Exception e) {
            log.error("매수 주문 생성 실패", e);
        }
    }

    /**
     * 매도 신호 실행
     */
    private void executeSellSignal(
            Strategy strategy,
            String symbol,
            double currentPrice) {
        
        try {
            // 현재 보유한 포지션 조회
            Optional<Position> positionOpt = positionRepository
                    .findByAccountAndSymbolAndStatus(
                            strategy.getAccount(),
                            symbol,
                            Position.PositionStatus.OPEN);
            
            if (positionOpt.isPresent()) {
                Position position = positionOpt.get();
                var order = executionEngine.executeSellSignal(
                        strategy.getAccount(),
                        symbol,
                        currentPrice,
                        position.getQuantity(),
                        null);
                
                orderService.createOrder(order);
                log.info("✓ 매도 주문 생성: {}, 수량: {}", symbol, position.getQuantity());
            }
        } catch (Exception e) {
            log.error("매도 주문 생성 실패", e);
        }
    }
}

