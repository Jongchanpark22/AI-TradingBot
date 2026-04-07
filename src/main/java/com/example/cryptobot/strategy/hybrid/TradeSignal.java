package com.example.cryptobot.strategy.hybrid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {

    private boolean tradable;
    private SignalType signalType;
    private String reason;
    private int score;

    public enum SignalType {
        STRONG_BUY,
        BUY,
        HOLD,
        SELL,
        STRONG_SELL,
        NO_SIGNAL
    }
}