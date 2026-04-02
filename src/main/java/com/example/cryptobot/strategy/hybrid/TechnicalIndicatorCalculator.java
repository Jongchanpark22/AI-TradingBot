package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.market.candle.Candle;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public class TechnicalIndicatorCalculator {

    public static double calculateSMA(List<Double> prices, int period) {
        if (prices == null || prices.size() < period) {
            return 0;
        }

        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }

    public static double calculateEMA(List<Double> prices, int period) {
        if (prices == null || prices.size() < period) {
            return 0;
        }

        double multiplier = 2.0 / (period + 1);
        double ema = 0;

        for (int i = 0; i < period; i++) {
            ema += prices.get(i);
        }
        ema /= period;

        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) * multiplier) + (ema * (1 - multiplier));
        }

        return ema;
    }

    public static List<Double> calculateEMASeries(List<Double> prices, int period) {
        List<Double> emaSeries = new ArrayList<>();
        if (prices == null || prices.size() < period) {
            return emaSeries;
        }

        double multiplier = 2.0 / (period + 1);
        double ema = 0;

        for (int i = 0; i < period; i++) {
            ema += prices.get(i);
        }
        ema /= period;

        emaSeries.add(ema);

        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) * multiplier) + (ema * (1 - multiplier));
            emaSeries.add(ema);
        }

        return emaSeries;
    }

    public static MACDValues calculateMACD(List<Double> closePrices) {
        if (closePrices == null || closePrices.size() < 35) {
            return null;
        }

        List<Double> ema12Series = calculateEMASeries(closePrices, 12);
        List<Double> ema26Series = calculateEMASeries(closePrices, 26);

        if (ema12Series.isEmpty() || ema26Series.isEmpty()) {
            return null;
        }

        int offset = 26 - 12;
        List<Double> macdSeries = new ArrayList<>();

        for (int i = 0; i < ema26Series.size(); i++) {
            double macd = ema12Series.get(i + offset) - ema26Series.get(i);
            macdSeries.add(macd);
        }

        if (macdSeries.size() < 9) {
            return null;
        }

        List<Double> signalSeries = calculateEMASeries(macdSeries, 9);
        if (signalSeries.isEmpty()) {
            return null;
        }

        double macd = macdSeries.get(macdSeries.size() - 1);
        double previousMacd = macdSeries.size() >= 2 ? macdSeries.get(macdSeries.size() - 2) : macd;
        double signalLine = signalSeries.get(signalSeries.size() - 1);
        double previousSignalLine = signalSeries.size() >= 2 ? signalSeries.get(signalSeries.size() - 2) : signalLine;
        double histogram = macd - signalLine;

        return MACDValues.builder()
                .macd(macd)
                .previousMacd(previousMacd)
                .signalLine(signalLine)
                .previousSignalLine(previousSignalLine)
                .histogram(histogram)
                .build();
    }

    public static double calculateRSI(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.size() < period + 1) {
            return 50;
        }

        double sumGain = 0;
        double sumLoss = 0;

        for (int i = closePrices.size() - period; i < closePrices.size(); i++) {
            double change = closePrices.get(i) - closePrices.get(i - 1);
            if (change > 0) sumGain += change;
            else sumLoss -= change;
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        if (avgLoss == 0) {
            return 100;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    public static double calculateVolumeMA(List<Double> volumes, int period) {
        return calculateSMA(volumes, period);
    }

    public static double calculateOBV(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }

        double obv = 0;
        for (Candle candle : candles) {
            if (candle.getClosePrice() == null || candle.getOpenPrice() == null) {
                continue;
            }

            int comparison = candle.getClosePrice().compareTo(candle.getOpenPrice());
            double volume = candle.getVolume() != null ? candle.getVolume().doubleValue() : 0;

            if (comparison > 0) obv += volume;
            else if (comparison < 0) obv -= volume;
        }
        return obv;
    }

    public static BollingerBands calculateBollingerBands(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.size() < period) {
            return null;
        }

        double middle = calculateSMA(closePrices, period);

        double sumSquaredDiff = 0;
        for (int i = closePrices.size() - period; i < closePrices.size(); i++) {
            double diff = closePrices.get(i) - middle;
            sumSquaredDiff += diff * diff;
        }

        double stdDev = Math.sqrt(sumSquaredDiff / period);
        double upper = middle + (stdDev * 2);
        double lower = middle - (stdDev * 2);

        return BollingerBands.builder()
                .upper(upper)
                .middle(middle)
                .lower(lower)
                .stdDev(stdDev)
                .build();
    }

    public static SupportResistance calculateSupportResistance(List<Candle> candles, int lookback) {
        if (candles == null || candles.size() < lookback) {
            return null;
        }

        double resistance = Double.NEGATIVE_INFINITY;
        double support = Double.POSITIVE_INFINITY;

        for (int i = candles.size() - lookback; i < candles.size(); i++) {
            Candle candle = candles.get(i);

            if (candle.getHighPrice() == null || candle.getLowPrice() == null) {
                continue;
            }

            double high = candle.getHighPrice().doubleValue();
            double low = candle.getLowPrice().doubleValue();

            resistance = Math.max(resistance, high);
            support = Math.min(support, low);
        }

        if (Double.isInfinite(resistance) || Double.isInfinite(support)) {
            return null;
        }

        return SupportResistance.builder()
                .support(support)
                .resistance(resistance)
                .build();
    }

    public static double calculatePriceDistanceFromSupport(double currentPrice, double support, double resistance) {
        if (Double.isInfinite(support) || Double.isInfinite(resistance)) return 0;
        if (Double.isNaN(support) || Double.isNaN(resistance)) return 0;

        double midpoint = (support + resistance) / 2;
        double range = resistance - support;
        if (range == 0) return 0;
        return (currentPrice - midpoint) / range;
    }

    @Builder
    @Data
    public static class MACDValues {
        private double macd;
        private double previousMacd;
        private double signalLine;
        private double previousSignalLine;
        private double histogram;
    }

    @Builder
    @Data
    public static class BollingerBands {
        private double upper;
        private double middle;
        private double lower;
        private double stdDev;
    }

    @Builder
    @Data
    public static class SupportResistance {
        private double support;
        private double resistance;
    }
}