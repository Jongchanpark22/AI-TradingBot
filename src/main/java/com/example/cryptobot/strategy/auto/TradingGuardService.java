package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.strategy.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TradingGuardService {

    private final TradingProperties tradingProperties;

    private final Map<String, LocalDateTime> lastExitTime = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastEntryTime = new ConcurrentHashMap<>();

    public boolean canEnter(String market) {
        LocalDateTime entry = lastEntryTime.get(market);
        if (entry != null && entry.plusMinutes(5).isAfter(LocalDateTime.now())) {
            return false;
        }

        LocalDateTime exit = lastExitTime.get(market);
        if (exit == null) {
            return true;
        }

        return exit.plusMinutes(tradingProperties.getReentryCooldownMinutes())
                .isBefore(LocalDateTime.now());
    }

    public void markEntered(String market) {
        lastEntryTime.put(market, LocalDateTime.now());
    }

    public void markExited(String market) {
        lastExitTime.put(market, LocalDateTime.now());
    }
}