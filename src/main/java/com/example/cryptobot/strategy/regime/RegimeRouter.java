package com.example.cryptobot.strategy.regime;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.Strategy;
import com.example.cryptobot.strategy.core.StrategySignal;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * 레짐을 분류하여 적합한 전략으로 라우팅하는 최상위 결정자.
 *
 * <pre>
 *   TRENDING_UP   → trendStrategy   (예: DonchianBreakout)
 *   RANGING       → rangeStrategy   (예: MeanReversion)
 *   TRENDING_DOWN → 관망 (no shorts)
 *   NEUTRAL       → 관망
 * </pre>
 *
 * <p>전략은 신호({@link StrategySignal})만 반환하고, 포지션 사이징은 여기서
 * {@code RiskManager}에 위임한다 — 전략 로직과 리스크 관리를 분리한다.
 */
public final class RegimeRouter {

    private final RegimeClassifier classifier;
    private final Strategy trendStrategy;
    private final Strategy rangeStrategy;

    public RegimeRouter(RegimeClassifier classifier,
                        Strategy trendStrategy,
                        Strategy rangeStrategy) {
        this.classifier = classifier;
        this.trendStrategy = trendStrategy;
        this.rangeStrategy = rangeStrategy;
    }

    /**
     * 현재 레짐을 분류하고 해당 전략의 신호를 {@link EntryPlan}으로 변환하여 반환한다.
     *
     * @param candles 최신 캔들 목록 (oldest → newest)
     * @param equity  계정 잔고
     * @param risk    리스크 매니저
     * @return 라우팅 결정 결과
     */
    public RoutedDecision decide(List<Candle> candles, double equity, RiskManager risk) {
        MarketRegime regime = classifier.classify(candles);
        return switch (regime) {
            case TRENDING_UP -> route(regime, trendStrategy, candles, equity, risk);
            case RANGING -> route(regime, rangeStrategy, candles, equity, risk);
            case TRENDING_DOWN, NEUTRAL -> new RoutedDecision(regime, "stand-aside", Optional.empty());
        };
    }

    private RoutedDecision route(MarketRegime regime, Strategy strategy,
                                 List<Candle> candles, double equity, RiskManager risk) {
        Optional<StrategySignal> signal = strategy.evaluate(candles);
        if (signal.isEmpty()) {
            return new RoutedDecision(regime, strategy.id(), Optional.empty());
        }
        StrategySignal s = signal.get();
        EntryPlan plan = risk.planLong(equity, s.entryPrice(), s.atr());
        Optional<EntryPlan> entry = plan.isExecutable() ? Optional.of(plan) : Optional.empty();
        return new RoutedDecision(regime, strategy.id(), entry);
    }

    /** 라우터 결정 결과. {@code plan}이 비어 있으면 진입하지 않는다. */
    public record RoutedDecision(MarketRegime regime, String strategyName, Optional<EntryPlan> plan) {
        public boolean shouldEnter() { return plan.isPresent(); }
    }
}
