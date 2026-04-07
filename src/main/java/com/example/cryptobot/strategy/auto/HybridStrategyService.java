package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.strategy.dto.HybridDecisionResult;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer.TradeSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HybridStrategyService {

    private final HybridSignalAnalyzer hybridSignalAnalyzer;

    public HybridDecisionResult evaluate(String market) {
        TradeSignal signal = hybridSignalAnalyzer.analyze(market);

        if (signal == null) {
            return HybridDecisionResult.builder()
                    .tradable(false)
                    .signal("NO_SIGNAL")
                    .reason("signal null")
                    .build();
        }

        return HybridDecisionResult.builder()
                .tradable(
                        signal.getSignal() == HybridSignalAnalyzer.SignalType.BUY
                                || signal.getSignal() == HybridSignalAnalyzer.SignalType.STRONG_BUY
                )
                .signal(signal.getSignal().name())
                .reason(signal.getReason())
                .score(signal.getScore())
                .build();
    }
}