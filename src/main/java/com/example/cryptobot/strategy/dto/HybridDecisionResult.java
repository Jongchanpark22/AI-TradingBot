package com.example.cryptobot.strategy.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HybridDecisionResult {
    private String market;
    private boolean tradable;
    private String signal;   // STRONG_BUY, BUY, HOLD, SELL, NO_SIGNAL
    private int score;
    private String reason;
}