package com.example.cryptobot.strategy.ai;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.regime.MarketRegime;

import java.time.ZoneOffset;
import java.util.List;

/**
 * FeatureSnapshot 공용 빌더.
 *
 * <p>HybridStrategyExecutor(라이브)와 HybridBacktestEngine(백테스트) 양쪽에서
 * 동일한 feature를 생성하도록 여기서 중앙화한다.
 * 인디케이터 계산 로직이 두 경로에서 분기하면 Python 모델의 학습/추론 분포가 어긋난다.
 */
public final class FeatureSnapshotFactory {

    private FeatureSnapshotFactory() {}

    /**
     * 이미 계산된 인디케이터 값과 HybridSignalAnalyzer 신호를 받아 FeatureSnapshot을 조립한다.
     *
     * @param symbol        심볼 (예: KRW-BTC)
     * @param timeframe     타임프레임 이름 (예: "15분")
     * @param window        신호 발생 시점까지의 캔들 윈도우 (look-ahead 없음)
     * @param ema12         EMA(12)
     * @param ema26         EMA(26)
     * @param sma50         SMA(50)
     * @param macd          MACD 값
     * @param macdSignal    MACD 시그널 라인
     * @param macdHist      MACD 히스토그램
     * @param rsi14         RSI(14)
     * @param volumeRatio   현재거래량 / volumeMA20
     * @param atr14         ATR(14)
     * @param regime        MarketRegime
     * @param trend         TrendSignal
     * @param momentum      MomentumSignal
     * @param rsiSig        RSISignal
     * @param volumeSig     VolumeSignal
     * @param candleSig     CandleSignal
     * @param tradeSignal   최종 TradeSignal (score, signal 포함)
     */
    /**
     * 시장 맥락 없이 FeatureSnapshot을 조립한다 (기존 호출부 호환).
     * 내부적으로 market* 필드는 기본값(UNKNOWN / 0 / 0.0)으로 채운다.
     */
    public static FeatureSnapshot build(
            String symbol,
            String timeframe,
            List<Candle> window,
            double ema12, double ema26, double sma50,
            double macd, double macdSignal, double macdHist,
            double rsi14, double volumeRatio, double atr14,
            MarketRegime regime,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSig,
            HybridSignalAnalyzer.VolumeSignal volumeSig,
            HybridSignalAnalyzer.CandleSignal candleSig,
            HybridSignalAnalyzer.TradeSignal tradeSignal,
            String strategyId,
            String strategyType) {
        return build(symbol, timeframe, window,
                ema12, ema26, sma50, macd, macdSignal, macdHist,
                rsi14, volumeRatio, atr14,
                regime, trend, momentum, rsiSig, volumeSig, candleSig, tradeSignal,
                strategyId, strategyType,
                "UNKNOWN", 0, 0.0, 0);
    }

    /**
     * 시장 맥락(BTC context)을 포함하여 FeatureSnapshot을 조립한다.
     *
     * @param marketRegime  BTC 레짐 문자열 (TRENDING_UP / RANGING / NEUTRAL / TRENDING_DOWN)
     * @param marketTrend   BTC EMA12 > EMA26 여부 (1/0)
     * @param marketReturn  BTC 최근 20봉 수익률 (%)
     * @param marketAboveMA BTC 현재가 > SMA50 여부 (1/0)
     */
    public static FeatureSnapshot build(
            String symbol,
            String timeframe,
            List<Candle> window,
            double ema12, double ema26, double sma50,
            double macd, double macdSignal, double macdHist,
            double rsi14, double volumeRatio, double atr14,
            MarketRegime regime,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSig,
            HybridSignalAnalyzer.VolumeSignal volumeSig,
            HybridSignalAnalyzer.CandleSignal candleSig,
            HybridSignalAnalyzer.TradeSignal tradeSignal,
            String strategyId,
            String strategyType,
            String marketRegime,
            int marketTrend,
            double marketReturn,
            int marketAboveMA) {

        // 마지막 완성된 캔들 (인덱스 size-2: 최신 봉은 형성 중)
        Candle signalBar = window.size() >= 2 ? window.get(window.size() - 2) : window.get(window.size() - 1);
        double close = signalBar.getClosePrice() != null ? signalBar.getClosePrice().doubleValue() : 0.0;
        long timestampMs = signalBar.getTimestamp() != null
                ? signalBar.getTimestamp().toEpochSecond(ZoneOffset.UTC) * 1000L
                : 0L;

        // ADX(14)
        Indicators.AdxValue adv = Indicators.adx(window, 14);
        double adx14 = adv.isValid() ? adv.adx() : 0.0;
        double plusDi = adv.isValid() ? adv.plusDi() : 0.0;
        double minusDi = adv.isValid() ? adv.minusDi() : 0.0;

        // 볼린저 밴드(20, 2.0)
        List<Double> closes = window.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();
        Indicators.BollingerBand bb = Indicators.bollinger(closes, 20, 2.0);
        double bbUpper = bb != null ? bb.upper() : 0.0;
        double bbLower = bb != null ? bb.lower() : 0.0;

        // Donchian(20) — 직전 20봉 기준 (look-ahead 방지)
        Indicators.DonchianChannel dc = Indicators.donchianExcludingLast(window, 20);
        double donchianHigh20 = dc != null ? dc.upper() : 0.0;

        // Supertrend(10, 3.0)
        Indicators.SupertrendPoint st = Indicators.supertrend(window, 10, 3.0);
        int supertrendDir = st != null ? (st.isUp() ? 1 : -1) : 0;

        // EMA 이격률
        double closeVsEma26Pct = ema26 > 0 ? (close - ema26) / ema26 * 100.0 : 0.0;

        return FeatureSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestampMs(timestampMs)
                .close(close)
                .ema12(ema12).ema26(ema26).sma50(sma50)
                .closeVsEma26Pct(closeVsEma26Pct)
                .macd(macd).macdSignal(macdSignal).macdHist(macdHist)
                .rsi14(rsi14)
                .adx14(adx14).plusDi(plusDi).minusDi(minusDi)
                .atr14(atr14)
                .bbUpper(bbUpper).bbLower(bbLower)
                .volumeRatio(volumeRatio)
                .donchianHigh20(donchianHigh20)
                .supertrendDir(supertrendDir)
                .regime(regime != null ? regime.name() : "UNKNOWN")
                .trendScore(encodeTrend(trend))
                .momentumScore(encodeMomentum(momentum))
                .rsiScore(encodeRsi(rsiSig))
                .candleScore(encodeCandle(candleSig))
                .volumeConfidence(encodeVolume(volumeSig))
                .totalScore(tradeSignal != null && tradeSignal.getScore() != null ? tradeSignal.getScore() : 0)
                .ruleSignal(tradeSignal != null ? tradeSignal.getSignal().name() : "NO_SIGNAL")
                .strategyId(strategyId != null ? strategyId : "UNKNOWN")
                .strategyType(strategyType != null ? strategyType : "UNKNOWN")
                .marketRegime(marketRegime != null ? marketRegime : "UNKNOWN")
                .marketTrend(marketTrend)
                .marketReturn(marketReturn)
                .marketAboveMA(marketAboveMA)
                .build();
    }

    // ---- 신호 열거형 → 숫자 인코딩 ----

    private static int encodeTrend(HybridSignalAnalyzer.TrendSignal t) {
        if (t == null) return 0;
        return switch (t) {
            case STRONG_UPTREND -> 2;
            case UPTREND -> 1;
            case SIDEWAYS -> 0;
            case DOWNTREND -> -1;
            case STRONG_DOWNTREND -> -2;
        };
    }

    private static int encodeMomentum(HybridSignalAnalyzer.MomentumSignal m) {
        if (m == null) return 0;
        return switch (m) {
            case STRONG_BUY -> 2;
            case BUY -> 1;
            case NEUTRAL -> 0;
            case SELL -> -1;
            case STRONG_SELL -> -2;
        };
    }

    private static int encodeRsi(HybridSignalAnalyzer.RSISignal r) {
        if (r == null) return 0;
        return switch (r) {
            case WEAK_BUY -> 1;
            case OVERBOUGHT -> -1;
            default -> 0;
        };
    }

    private static int encodeCandle(HybridSignalAnalyzer.CandleSignal c) {
        if (c == null) return 0;
        return switch (c) {
            case STRONG_BULLISH, HAMMER, CONSECUTIVE_BULLISH -> 1;
            case STRONG_BEARISH, SHOOTING_STAR -> -1;
            default -> 0;
        };
    }

    private static int encodeVolume(HybridSignalAnalyzer.VolumeSignal v) {
        if (v == null) return 0;
        return switch (v) {
            case VERY_LOW_CONFIDENCE -> 0;
            case LOW_CONFIDENCE -> 1;
            case NORMAL -> 2;
            case HIGH_CONFIDENCE -> 3;
            case VERY_HIGH_CONFIDENCE -> 4;
        };
    }
}
