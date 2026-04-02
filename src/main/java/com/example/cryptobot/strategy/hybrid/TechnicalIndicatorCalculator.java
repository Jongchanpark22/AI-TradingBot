package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.market.candle.Candle;
import lombok.Data;
import java.util.List;

/**
 * 기술적 지표 계산 서비스
 * - 이동평균 (EMA, SMA)
 * - MACD
 * - RSI
 * - 거래량 분석
 */
@Data
public class TechnicalIndicatorCalculator {

    // ====== 이동평균 계산 ======
    
    /**
     * 단순 이동평균 (Simple Moving Average)
     */
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

    /**
     * 지수 이동평균 (Exponential Moving Average)
     */
    public static double calculateEMA(List<Double> prices, int period) {
        if (prices == null || prices.size() < period) {
            return 0;
        }
        
        double sma = calculateSMA(prices, period);
        double multiplier = 2.0 / (period + 1);
        
        double ema = sma;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    /**
     * MACD (Moving Average Convergence Divergence) 계산
     * MACD = EMA(12) - EMA(26)
     * Signal Line = EMA(MACD, 9)
     * Histogram = MACD - Signal
     */
    public static MACDValues calculateMACD(List<Double> closePrices) {
        if (closePrices == null || closePrices.size() < 26) {
            return null;
        }
        
        double ema12 = calculateEMA(closePrices, 12);
        double ema26 = calculateEMA(closePrices, 26);
        double macd = ema12 - ema26;
        
        // 신호선 계산 (마지막 9개 MACD 값이 필요)
        List<Double> closePricesSublist = closePrices.subList(
                Math.max(0, closePrices.size() - 34),
                closePrices.size()
        );
        double signalLine = calculateEMA(closePricesSublist, 9);
        double histogram = macd - signalLine;
        
        return MACDValues.builder()
                .macd(macd)
                .signalLine(signalLine)
                .histogram(histogram)
                .build();
    }

    /**
     * RSI (Relative Strength Index) 계산
     * RSI = 100 - (100 / (1 + RS))
     * RS = 평균 상승폭 / 평균 하강폭
     */
    public static double calculateRSI(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.size() < period + 1) {
            return 50; // 중립값
        }
        
        double sumGain = 0;
        double sumLoss = 0;
        
        for (int i = closePrices.size() - period; i < closePrices.size(); i++) {
            double change = closePrices.get(i) - closePrices.get(i - 1);
            if (change > 0) {
                sumGain += change;
            } else {
                sumLoss -= change;
            }
        }
        
        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;
        
        if (avgLoss == 0) {
            return 100;
        }
        
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * 거래량 이동평균
     */
    public static double calculateVolumeMA(List<Double> volumes, int period) {
        return calculateSMA(volumes, period);
    }

    /**
     * OBV (On Balance Volume) 계산
     * OBV는 거래량을 가격 방향에 따라 누적
     */
    public static double calculateOBV(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        
        double obv = 0;
        for (Candle candle : candles) {
            if (candle.getClosePrice() > candle.getOpenPrice()) {
                obv += candle.getVolume();
            } else if (candle.getClosePrice() < candle.getOpenPrice()) {
                obv -= candle.getVolume();
            }
        }
        return obv;
    }

    /**
     * 볼린저 밴드 (Bollinger Bands)
     * 중앙선: 20일 SMA
     * 상단: 중앙선 + (표준편차 × 2)
     * 하단: 중앙선 - (표준편차 × 2)
     */
    public static BollingerBands calculateBollingerBands(List<Double> closePrices, int period) {
        if (closePrices == null || closePrices.size() < period) {
            return null;
        }
        
        double middle = calculateSMA(closePrices, period);
        
        // 표준편차 계산
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

    /**
     * 지지선/저항선 계산 (최근 N개 봉의 고가/저가)
     */
    public static SupportResistance calculateSupportResistance(List<Candle> candles, int lookback) {
        if (candles == null || candles.size() < lookback) {
            return null;
        }
        
        double resistance = Double.MIN_VALUE;
        double support = Double.MAX_VALUE;
        
        for (int i = candles.size() - lookback; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            resistance = Math.max(resistance, candle.getHighPrice());
            support = Math.min(support, candle.getLowPrice());
        }
        
        return SupportResistance.builder()
                .support(support)
                .resistance(resistance)
                .build();
    }

    /**
     * 수익성 지표: 가격이 지지선/저항선에서 얼마나 멀어졌는가
     * 음수: 지지선 근처 (매수 기회)
     * 양수: 저항선 근처 (매도 기회)
     */
    public static double calculatePriceDistanceFromSupport(
            double currentPrice,
            double support,
            double resistance) {
        
        double midpoint = (support + resistance) / 2;
        double range = resistance - support;
        
        if (range == 0) return 0;
        
        return (currentPrice - midpoint) / range;
    }

    // ====== DTO ======
    
    @lombok.Builder
    @lombok.Data
    public static class MACDValues {
        private double macd;          // MACD 값
        private double signalLine;    // 신호선
        private double histogram;     // 히스토그램
    }

    @lombok.Builder
    @lombok.Data
    public static class BollingerBands {
        private double upper;         // 상단선
        private double middle;        // 중앙선
        private double lower;         // 하단선
        private double stdDev;        // 표준편차
    }

    @lombok.Builder
    @lombok.Data
    public static class SupportResistance {
        private double support;       // 지지선
        private double resistance;    // 저항선
    }
}

