package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.upbit.service.UpbitMarketService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import com.example.cryptobot.strategy.hybrid.TechnicalIndicatorCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateCoinSelectorService {

    private final UpbitMarketService upbitMarketService;

    private final HybridSignalAnalyzer signalAnalyzer;

    public Optional<CoinScoreResult> selectBestCandidate(List<String> candidateMarkets) {
        List<CoinScoreResult> results = candidateMarkets.stream()
                .map(this::analyzeMarket)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(CoinScoreResult::getTotalScore).reversed())
                .toList();

        if (results.isEmpty()) {
            log.warn("[후보선택] 분석 가능한 코인이 없음");
            return Optional.empty();
        }

        CoinScoreResult best = results.get(0);

        log.info("========== 후보 코인 점수 순위 ==========");
        results.forEach(result -> log.info(
                "[후보] market={}, score={}, tradable={}, signal={}, reason={}",
                result.getMarket(),
                result.getTotalScore(),
                result.isTradable(),
                result.getTradeSignal() != null ? result.getTradeSignal().getSignal() : null,
                result.getReason()
        ));
        log.info("[최종선택] market={}, score={}, signal={}",
                best.getMarket(),
                best.getTotalScore(),
                best.getTradeSignal() != null ? best.getTradeSignal().getSignal() : null
        );

        return Optional.of(best);
    }

    private CoinScoreResult analyzeMarket(String market) {
        try {
            List<Candle> candles = upbitMarketService.getAndSaveCandles(market, 15, 60);
            Ticker ticker = upbitMarketService.getAndSaveTicker(market);

            if (ticker == null || candles == null || candles.size() < 50) {
                log.warn("[후보분석 실패] market={}, tickerOrCandles 부족", market);
                return null;
            }

            candles.sort(Comparator.comparing(Candle::getTimestamp));

            List<Double> closePrices = candles.stream()
                    .map(c -> c.getClosePrice().doubleValue())
                    .toList();

            List<Double> volumes = candles.stream()
                    .map(c -> c.getVolume().doubleValue())
                    .toList();

            double ema12 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 12);
            double ema26 = TechnicalIndicatorCalculator.calculateEMA(closePrices, 26);
            double sma50 = TechnicalIndicatorCalculator.calculateSMA(closePrices, 50);

            TechnicalIndicatorCalculator.MACDValues macdValues =
                    TechnicalIndicatorCalculator.calculateMACD(closePrices);

            if (macdValues == null) {
                log.warn("[후보분석 실패] market={}, macd 계산 실패", market);
                return null;
            }

            double rsi = TechnicalIndicatorCalculator.calculateRSI(closePrices, 14);
            double volumeMA20 = TechnicalIndicatorCalculator.calculateVolumeMA(volumes, 20);
            double currentVolume = candles.get(candles.size() - 1).getVolume().doubleValue();
            double volumeRatio = volumeMA20 > 0 ? currentVolume / volumeMA20 : 0.0;

            Candle last = candles.get(candles.size() - 1);

            HybridSignalAnalyzer.TrendSignal trend =
                    signalAnalyzer.analyzeTrend(ema12, ema26, sma50);

            HybridSignalAnalyzer.MomentumSignal momentum = signalAnalyzer.analyzeMacd(
                    macdValues.getMacd(),
                    macdValues.getSignalLine(),
                    macdValues.getPreviousMacd(),
                    macdValues.getPreviousSignalLine()
            );

            HybridSignalAnalyzer.RSISignal rsiSignal = signalAnalyzer.analyzeRSI(rsi);
            HybridSignalAnalyzer.VolumeSignal volumeSignal = signalAnalyzer.analyzeVolume(currentVolume, volumeMA20);
            HybridSignalAnalyzer.CandleSignal candleSignal = signalAnalyzer.analyzeCandlePattern(
                    last.getOpenPrice().doubleValue(),
                    last.getHighPrice().doubleValue(),
                    last.getLowPrice().doubleValue(),
                    last.getClosePrice().doubleValue()
            );

            HybridSignalAnalyzer.TradeSignal tradeSignal = signalAnalyzer.generateTradeSignal(
                    trend, momentum, rsiSignal, volumeSignal, candleSignal
            );

            int score = calculateTotalScore(
                    trend, momentum, rsiSignal, volumeSignal, candleSignal, tradeSignal
            );

            boolean tradable =
                    tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                            || tradeSignal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY;

            return CoinScoreResult.builder()
                    .market(market)
                    .totalScore(score)
                    .tradable(tradable)
                    .reason(tradeSignal.getReason())
                    .currentPrice(ticker.getCurrentPrice().doubleValue())
                    .ema12(ema12)
                    .ema26(ema26)
                    .sma50(sma50)
                    .rsi(rsi)
                    .volumeRatio(volumeRatio)
                    .trendSignal(trend)
                    .momentumSignal(momentum)
                    .rsiSignal(rsiSignal)
                    .volumeSignal(volumeSignal)
                    .candleSignal(candleSignal)
                    .tradeSignal(tradeSignal)
                    .build();

        } catch (Exception e) {
            log.error("[후보분석 예외] market={}", market, e);
            return null;
        }
    }

    private int calculateTotalScore(
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSignal,
            HybridSignalAnalyzer.VolumeSignal volumeSignal,
            HybridSignalAnalyzer.CandleSignal candleSignal,
            HybridSignalAnalyzer.TradeSignal tradeSignal
    ) {
        int score = 0;

        score += switch (trend) {
            case STRONG_UPTREND -> 25;
            case UPTREND -> 18;
            case SIDEWAYS -> 8;
            case DOWNTREND -> 0;
            case STRONG_DOWNTREND -> -5;
        };

        score += switch (momentum) {
            case STRONG_BUY -> 20;
            case BUY -> 15;
            case NEUTRAL -> 5;
            case SELL -> 0;
            case STRONG_SELL -> -5;
        };

        score += switch (rsiSignal) {
            case OVERSOLD -> 12;
            case WEAK_BUY -> 10;
            case NEUTRAL -> 6;
            case WEAK_SELL -> 2;
            case OVERBOUGHT -> 0;
        };

        score += switch (volumeSignal) {
            case VERY_HIGH_CONFIDENCE -> 18;
            case HIGH_CONFIDENCE -> 14;
            case NORMAL -> 8;
            case LOW_CONFIDENCE -> 2;
            case VERY_LOW_CONFIDENCE -> 0;
        };

        score += switch (candleSignal) {
            case HAMMER, STRONG_BULLISH -> 10;
            case DOJI, NEUTRAL -> 5;
            case SHOOTING_STAR, STRONG_BEARISH -> 0;
        };

        score += tradeSignal.getConfidence() / 5;

        return Math.max(score, 0);
    }
}