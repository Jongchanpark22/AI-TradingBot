package com.example.cryptobot.strategy.hybrid;

import lombok.Builder;
import lombok.Data;

/**
 * 하이브리드 거래 전략 신호 분석기
 */
public class HybridSignalAnalyzer {

    public TrendSignal analyzeTrend(double ema12, double ema26, double sma50) {
        if (ema12 > ema26 && ema26 > sma50) return TrendSignal.STRONG_UPTREND;
        else if (ema12 > ema26) return TrendSignal.UPTREND;
        else if (ema12 < ema26 && ema26 < sma50) return TrendSignal.STRONG_DOWNTREND;
        else if (ema12 < ema26) return TrendSignal.DOWNTREND;
        return TrendSignal.SIDEWAYS;
    }

    public MomentumSignal analyzeMacd(double macd, double signalLine, double previousMacd) {
        double histogram = macd - signalLine;
        double previousHistogram = previousMacd - signalLine;
        
        if (macd > signalLine && macd > 0) {
            return (previousHistogram < 0) ? MomentumSignal.STRONG_BUY : MomentumSignal.BUY;
        } else if (macd < signalLine && macd < 0) {
            return (previousHistogram > 0) ? MomentumSignal.STRONG_SELL : MomentumSignal.SELL;
        }
        return MomentumSignal.NEUTRAL;
    }

    public RSISignal analyzeRSI(double rsi) {
        if (rsi < 30) return RSISignal.OVERSOLD;
        else if (rsi < 40) return RSISignal.WEAK_SELL;
        else if (rsi > 70) return RSISignal.OVERBOUGHT;
        else if (rsi > 60) return RSISignal.WEAK_BUY;
        return RSISignal.NEUTRAL;
    }

    public VolumeSignal analyzeVolume(double currentVolume, double volumeMA20) {
        double volumeRatio = currentVolume / volumeMA20;
        if (volumeRatio >= 1.5) return VolumeSignal.VERY_HIGH_CONFIDENCE;
        else if (volumeRatio >= 1.3) return VolumeSignal.HIGH_CONFIDENCE;
        else if (volumeRatio >= 1.0) return VolumeSignal.NORMAL;
        else if (volumeRatio >= 0.8) return VolumeSignal.LOW_CONFIDENCE;
        return VolumeSignal.VERY_LOW_CONFIDENCE;
    }

    public CandleSignal analyzeCandlePattern(double open, double high, double low, double close) {
        double bodySize = Math.abs(close - open);
        double upperWick = high - Math.max(open, close);
        double lowerWick = Math.min(open, close) - low;
        double totalRange = high - low;
        
        if (totalRange == 0) return CandleSignal.DOJI;
        
        if (close > open && bodySize < totalRange * 0.3 && lowerWick > totalRange * 0.5) 
            return CandleSignal.HAMMER;
        if (close < open && bodySize < totalRange * 0.3 && upperWick > totalRange * 0.5) 
            return CandleSignal.SHOOTING_STAR;
        if (close > open && bodySize > upperWick * 2 && bodySize > lowerWick * 2) 
            return CandleSignal.STRONG_BULLISH;
        if (close < open && bodySize > upperWick * 2 && bodySize > lowerWick * 2) 
            return CandleSignal.STRONG_BEARISH;
        if (bodySize < totalRange * 0.1) return CandleSignal.DOJI;
        
        return CandleSignal.NEUTRAL;
    }

    public TradeSignal generateTradeSignal(TrendSignal trend, MomentumSignal momentum, RSISignal rsi, VolumeSignal volume, CandleSignal candle) {
        int bullishScore = 0, bearishScore = 0;
        
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
        
        if (volume == VolumeSignal.VERY_LOW_CONFIDENCE || volume == VolumeSignal.LOW_CONFIDENCE) {
            return TradeSignal.builder().signal(SignalType.NO_SIGNAL).confidence(0).reason("거래량 부족").build();
        }
        
        if (bullishScore >= 4 && volume == VolumeSignal.VERY_HIGH_CONFIDENCE) {
            return TradeSignal.builder().signal(SignalType.STRONG_BUY).confidence(95).reason("완벽한 매수").score(bullishScore).build();
        } else if (bullishScore >= 3 && volume.ordinal() >= VolumeSignal.HIGH_CONFIDENCE.ordinal()) {
            return TradeSignal.builder().signal(SignalType.BUY).confidence(80).reason("강한 매수").score(bullishScore).build();
        } else if (bearishScore >= 4 && volume == VolumeSignal.VERY_HIGH_CONFIDENCE) {
            return TradeSignal.builder().signal(SignalType.STRONG_SELL).confidence(95).reason("완벽한 매도").score(bearishScore).build();
        } else if (bearishScore >= 3 && volume.ordinal() >= VolumeSignal.HIGH_CONFIDENCE.ordinal()) {
            return TradeSignal.builder().signal(SignalType.SELL).confidence(80).reason("강한 매도").score(bearishScore).build();
        }
        
        return TradeSignal.builder().signal(SignalType.NO_SIGNAL).confidence(0).reason("신호 미충분").build();
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

