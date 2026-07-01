package com.example.cryptobot.strategy.core;

import com.example.cryptobot.market.candle.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StrategyRepository extends JpaRepository<StrategyConfig, Long> {

    List<StrategyConfig> findByStatusAndTargetPeriodAndAccount_IsActive(
            StrategyConfig.StrategyStatus status,
            Candle.CandlePeriod targetPeriod,
            boolean accountIsActive
    );
}