package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinScoreResult {

    private String market;
    private int totalScore;
    private boolean tradable;
    private String reason;

    private double currentPrice;
    private double ema12;
    private double ema26;
    private double sma50;
    private double rsi;
    private double volumeRatio;

    private HybridSignalAnalyzer.TrendSignal trendSignal;
    private HybridSignalAnalyzer.MomentumSignal momentumSignal;
    private HybridSignalAnalyzer.RSISignal rsiSignal;
    private HybridSignalAnalyzer.VolumeSignal volumeSignal;
    private HybridSignalAnalyzer.CandleSignal candleSignal;
    private HybridSignalAnalyzer.TradeSignal tradeSignal;
}