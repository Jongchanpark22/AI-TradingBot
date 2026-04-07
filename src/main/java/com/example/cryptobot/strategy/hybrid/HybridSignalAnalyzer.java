package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.upbit.service.UpbitMarketService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HybridSignalAnalyzer {

    private static final int ANALYSIS_CANDLE_COUNT = 120;
    private static final int MIN_REQUIRED_CANDLES = 60;
    private static final Candle.CandlePeriod ANALYSIS_PERIOD = Candle.CandlePeriod.ONE_HOUR;
    private static final int ANALYSIS_UNIT = 60;

    private final CandleRepository candleRepository;
    private final UpbitMarketService upbitMarketService;

    public TradeSignal analyze(String market) {
        syncLatestCandles(market);

        List<Candle> candles = loadRecentCandles(market);
        if (candles.size() < MIN_REQUIRED_CANDLES + 1) {
            return noSignal("분석용 캔들 데이터 부족: " + market);
        }

        List<Candle> closedCandles = getClosedCandles(candles);
        if (closedCandles.size() < MIN_REQUIRED_CANDLES) {
            return noSignal("완성 캔들 부족: " + market);
        }

        List<Double> closes = extractClosePrices(closedCandles);
        List<Double> volumes = extractVolumes(closedCandles);

        double ema12 = ema(closes, 12);
        double ema26 = ema(closes, 26);
        double sma50 = sma(closes, 50);

        List<Double> macdLine = buildMacdLine(closes);
        if (macdLine.size() < 10) {
            return noSignal("MACD 계산 데이터 부족: " + market);
        }

        double macd = macdLine.get(macdLine.size() - 1);
        double previousMacd = macdLine.get(macdLine.size() - 2);
        double signalLine = ema(macdLine, 9);
        double previousSignalLine = ema(macdLine.subList(0, macdLine.size() - 1), 9);
        double rsi = rsi(closes, 14);

        double currentVolume = volumes.get(volumes.size() - 1);
        double volumeMa20 = calculatePreviousVolumeMa20(volumes);
        double volumeRatio = volumeMa20 > 0 ? currentVolume / volumeMa20 : 0.0;

        Candle latest = closedCandles.get(closedCandles.size() - 1);

        TrendSignal trend = analyzeTrend(ema12, ema26, sma50);
        MomentumSignal momentum = analyzeMacd(macd, signalLine, previousMacd, previousSignalLine);
        RSISignal rsiSignal = analyzeRSI(rsi);
        VolumeSignal volumeSignal = analyzeVolume(currentVolume, volumeMa20);
        CandleSignal candleSignal = analyzeCandlePattern(
                toDouble(latest.getOpenPrice()),
                toDouble(latest.getHighPrice()),
                toDouble(latest.getLowPrice()),
                toDouble(latest.getClosePrice())
        );

        TradeSignal result = generateTradeSignal(trend, momentum, rsiSignal, volumeSignal, candleSignal);

        result.setReason(
                result.getReason()
                        + " | trend=" + trend
                        + ", momentum=" + momentum
                        + ", rsi=" + String.format("%.2f", rsi)
                        + ", volume=" + volumeSignal
                        + ", volumeRatio=" + String.format("%.2f", volumeRatio)
                        + ", candle=" + candleSignal
        );

        return result;
    }

    private void syncLatestCandles(String market) {
        upbitMarketService.getAndSaveCandles(market, ANALYSIS_UNIT, ANALYSIS_CANDLE_COUNT);
    }

    private List<Candle> loadRecentCandles(String market) {
        return candleRepository.findBySymbolAndPeriodOrderByTimestampDesc(
                market,
                ANALYSIS_PERIOD,
                PageRequest.of(0, ANALYSIS_CANDLE_COUNT)
        );
    }

    private List<Candle> getClosedCandles(List<Candle> candles) {
        List<Candle> orderedCandles = new ArrayList<>(candles);
        orderedCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // 마지막 봉은 진행 중일 수 있으므로 제외
        return orderedCandles.subList(0, orderedCandles.size() - 1);
    }

    private List<Double> extractClosePrices(List<Candle> candles) {
        return candles.stream()
                .map(Candle::getClosePrice)
                .map(this::toDouble)
                .toList();
    }

    private List<Double> extractVolumes(List<Candle> candles) {
        return candles.stream()
                .map(Candle::getVolume)
                .map(this::toDouble)
                .toList();
    }

    private double calculatePreviousVolumeMa20(List<Double> volumes) {
        if (volumes.size() < 2) {
            return 0.0;
        }
        return sma(volumes.subList(0, volumes.size() - 1), 20);
    }

    private TradeSignal noSignal(String reason) {
        return TradeSignal.builder()
                .signal(SignalType.NO_SIGNAL)
                .confidence(0)
                .reason(reason)
                .score(0)
                .build();
    }

    public TrendSignal analyzeTrend(double ema12, double ema26, double sma50) {
        if (ema12 > ema26 && ema26 > sma50) return TrendSignal.STRONG_UPTREND;
        if (ema12 > ema26) return TrendSignal.UPTREND;
        if (ema12 < ema26 && ema26 < sma50) return TrendSignal.STRONG_DOWNTREND;
        if (ema12 < ema26) return TrendSignal.DOWNTREND;
        return TrendSignal.SIDEWAYS;
    }

    public MomentumSignal analyzeMacd(
            double macd,
            double signalLine,
            double previousMacd,
            double previousSignalLine
    ) {
        double histogram = macd - signalLine;
        double previousHistogram = previousMacd - previousSignalLine;

        if (macd > signalLine && macd > 0) {
            return (previousHistogram <= 0 && histogram > 0)
                    ? MomentumSignal.STRONG_BUY
                    : MomentumSignal.BUY;
        }

        if (macd < signalLine && macd < 0) {
            return (previousHistogram >= 0 && histogram < 0)
                    ? MomentumSignal.STRONG_SELL
                    : MomentumSignal.SELL;
        }

        return MomentumSignal.NEUTRAL;
    }

    public RSISignal analyzeRSI(double rsi) {
        if (rsi < 30) return RSISignal.OVERSOLD;
        if (rsi < 40) return RSISignal.WEAK_SELL;
        if (rsi > 70) return RSISignal.OVERBOUGHT;
        if (rsi > 60) return RSISignal.WEAK_BUY;
        return RSISignal.NEUTRAL;
    }

    public VolumeSignal analyzeVolume(double currentVolume, double volumeMA20) {
        if (volumeMA20 <= 0) {
            return VolumeSignal.VERY_LOW_CONFIDENCE;
        }

        double volumeRatio = currentVolume / volumeMA20;

        if (volumeRatio >= 1.20) return VolumeSignal.VERY_HIGH_CONFIDENCE;
        if (volumeRatio >= 1.00) return VolumeSignal.HIGH_CONFIDENCE;
        if (volumeRatio >= 0.70) return VolumeSignal.NORMAL;
        if (volumeRatio >= 0.40) return VolumeSignal.LOW_CONFIDENCE;
        return VolumeSignal.VERY_LOW_CONFIDENCE;
    }

    public CandleSignal analyzeCandlePattern(double open, double high, double low, double close) {
        double bodySize = Math.abs(close - open);
        double upperWick = high - Math.max(open, close);
        double lowerWick = Math.min(open, close) - low;
        double totalRange = high - low;

        if (totalRange == 0) return CandleSignal.DOJI;

        if (close > open && bodySize < totalRange * 0.3 && lowerWick > totalRange * 0.5) {
            return CandleSignal.HAMMER;
        }
        if (close < open && bodySize < totalRange * 0.3 && upperWick > totalRange * 0.5) {
            return CandleSignal.SHOOTING_STAR;
        }
        if (close > open && bodySize > upperWick * 2 && bodySize > lowerWick * 2) {
            return CandleSignal.STRONG_BULLISH;
        }
        if (close < open && bodySize > upperWick * 2 && bodySize > lowerWick * 2) {
            return CandleSignal.STRONG_BEARISH;
        }
        if (bodySize < totalRange * 0.1) {
            return CandleSignal.DOJI;
        }

        return CandleSignal.NEUTRAL;
    }

    public TradeSignal generateTradeSignal(
            TrendSignal trend,
            MomentumSignal momentum,
            RSISignal rsi,
            VolumeSignal volume,
            CandleSignal candle
    ) {
        int bullishScore = 0;
        int bearishScore = 0;

        if (trend == TrendSignal.STRONG_UPTREND) bullishScore += 2;
        else if (trend == TrendSignal.UPTREND) bullishScore += 1;
        else if (trend == TrendSignal.STRONG_DOWNTREND) bearishScore += 2;
        else if (trend == TrendSignal.DOWNTREND) bearishScore += 1;

        if (momentum == MomentumSignal.STRONG_BUY) bullishScore += 2;
        else if (momentum == MomentumSignal.BUY) bullishScore += 1;
        else if (momentum == MomentumSignal.STRONG_SELL) bearishScore += 2;
        else if (momentum == MomentumSignal.SELL) bearishScore += 1;

        if (rsi == RSISignal.OVERSOLD || rsi == RSISignal.WEAK_BUY) bullishScore += 1;
        if (rsi == RSISignal.OVERBOUGHT || rsi == RSISignal.WEAK_SELL) bearishScore += 1;

        if (candle == CandleSignal.STRONG_BULLISH || candle == CandleSignal.HAMMER) bullishScore += 1;
        if (candle == CandleSignal.STRONG_BEARISH || candle == CandleSignal.SHOOTING_STAR) bearishScore += 1;

        if (volume == VolumeSignal.VERY_LOW_CONFIDENCE) {
            return TradeSignal.builder()
                    .signal(SignalType.NO_SIGNAL)
                    .confidence(0)
                    .reason("거래량 매우 부족")
                    .score(0)
                    .build();
        }

        if (bullishScore >= 4 && volume.ordinal() >= VolumeSignal.HIGH_CONFIDENCE.ordinal()) {
            return TradeSignal.builder()
                    .signal(SignalType.STRONG_BUY)
                    .confidence(90)
                    .reason("강한 매수")
                    .score(bullishScore)
                    .build();
        }

        if (bullishScore >= 3 && volume.ordinal() >= VolumeSignal.NORMAL.ordinal()) {
            return TradeSignal.builder()
                    .signal(SignalType.BUY)
                    .confidence(75)
                    .reason("매수")
                    .score(bullishScore)
                    .build();
        }

        if (bearishScore >= 4 && volume.ordinal() >= VolumeSignal.HIGH_CONFIDENCE.ordinal()) {
            return TradeSignal.builder()
                    .signal(SignalType.STRONG_SELL)
                    .confidence(90)
                    .reason("강한 매도")
                    .score(bearishScore)
                    .build();
        }

        if (bearishScore >= 3 && volume.ordinal() >= VolumeSignal.NORMAL.ordinal()) {
            return TradeSignal.builder()
                    .signal(SignalType.SELL)
                    .confidence(75)
                    .reason("매도")
                    .score(bearishScore)
                    .build();
        }

        return TradeSignal.builder()
                .signal(SignalType.NO_SIGNAL)
                .confidence(0)
                .reason("신호 미충분")
                .score(Math.max(bullishScore, bearishScore))
                .build();
    }

    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private double sma(List<Double> values, int period) {
        if (values.isEmpty()) return 0.0;

        int fromIndex = Math.max(0, values.size() - period);
        return values.subList(fromIndex, values.size())
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double ema(List<Double> values, int period) {
        if (values.isEmpty()) return 0.0;

        double multiplier = 2.0 / (period + 1.0);
        double ema = values.get(0);

        for (int i = 1; i < values.size(); i++) {
            ema = ((values.get(i) - ema) * multiplier) + ema;
        }
        return ema;
    }

    private List<Double> buildMacdLine(List<Double> closes) {
        List<Double> macdLine = new ArrayList<>();

        for (int i = 26; i <= closes.size(); i++) {
            List<Double> subset = closes.subList(0, i);
            macdLine.add(ema(subset, 12) - ema(subset, 26));
        }

        return macdLine;
    }

    private double rsi(List<Double> closes, int period) {
        if (closes.size() <= period) return 50.0;

        int start = closes.size() - period - 1;
        double gain = 0.0;
        double loss = 0.0;

        for (int i = start + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff > 0) gain += diff;
            else loss += Math.abs(diff);
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public enum TrendSignal { STRONG_UPTREND, UPTREND, SIDEWAYS, DOWNTREND, STRONG_DOWNTREND }
    public enum MomentumSignal { STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL }
    public enum RSISignal { OVERSOLD, WEAK_SELL, NEUTRAL, WEAK_BUY, OVERBOUGHT }
    public enum VolumeSignal { VERY_LOW_CONFIDENCE, LOW_CONFIDENCE, NORMAL, HIGH_CONFIDENCE, VERY_HIGH_CONFIDENCE }
    public enum CandleSignal { HAMMER, SHOOTING_STAR, STRONG_BULLISH, STRONG_BEARISH, DOJI, NEUTRAL }
    public enum SignalType { STRONG_BUY, BUY, WEAK_BUY, NO_SIGNAL, WEAK_SELL, SELL, STRONG_SELL }

    @Builder
    @Data
    public static class TradeSignal {
        private SignalType signal;
        private int confidence;
        private String reason;
        private Integer score;
    }
}