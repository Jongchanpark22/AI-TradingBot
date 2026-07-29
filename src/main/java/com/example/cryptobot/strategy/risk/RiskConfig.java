package com.example.cryptobot.strategy.risk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ATR 기반 리스크 파라미터 설정.
 *
 * <p>application.yml {@code trading.risk.*} 값을 읽어
 * {@link RiskParameters}와 {@link RiskManager} 빈을 생성한다.
 * 라이브(PositionMonitor, HybridStrategyExecutor)·백테스트 엔진 모두
 * 동일한 빈을 참조하여 train-serve 일관성을 보장한다.
 */
@Configuration
public class RiskConfig {

    @Value("${trading.risk.stop-atr-mult:3.0}")
    private double stopAtrMult;

    @Value("${trading.risk.take-profit-r:3.0}")
    private double takeProfitR;

    @Value("${trading.risk.partial-exit-r:1.0}")
    private double partialExitR;

    @Value("${trading.risk.partial-exit-ratio:0.5}")
    private double partialExitRatio;

    @Value("${trading.risk.partial2-r:2.0}")
    private double partial2R;

    @Value("${trading.risk.partial2-ratio:0.5}")
    private double partial2Ratio;

    @Value("${trading.risk.trail-atr-mult:3.0}")
    private double trailAtrMult;

    @Value("${trading.risk.trail-atr-mult-after-p2:1.5}")
    private double trailAtrMultAfterP2;

    @Value("${trading.risk.max-open-positions:3}")
    private int maxOpenPositions;

    @Bean
    public RiskParameters riskParameters() {
        return new RiskParameters(
                0.01,
                stopAtrMult,
                takeProfitR,
                partialExitR,
                partialExitRatio,
                partial2R,
                partial2Ratio,
                trailAtrMult,
                trailAtrMultAfterP2,
                0.05,
                maxOpenPositions);
    }

    @Bean
    public RiskManager riskManager(RiskParameters riskParameters) {
        return new RiskManager(riskParameters);
    }
}
