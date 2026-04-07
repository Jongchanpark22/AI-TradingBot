package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.strategy.hybrid.TradeExecutionService;
import com.example.cryptobot.upbit.dto.UpbitTickerDto;
import com.example.cryptobot.upbit.service.UpbitMarketService;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.strategy.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionMonitoringScheduler {

    private final PositionRepository positionRepository;
    private final UpbitMarketService upbitMarketService;
    private final TradeExecutionService tradeExecutionService;
    private final TradingProperties tradingProperties;
    private final TradingGuardService tradingGuardService;

    @Scheduled(fixedDelay = 30000)
    public void monitorOpenPositions() {
        List<Position> openPositions = positionRepository.findAllOpenPositions();

        for (Position position : openPositions) {
            try {
                String market = position.getSymbol();

                UpbitTickerDto ticker = upbitMarketService.getTicker(market);
                if (ticker == null || ticker.getTradePrice() == null) {
                    continue;
                }

                BigDecimal currentPrice = ticker.getTradePrice();
                BigDecimal avgBuyPrice = position.getAvgBuyPrice();
                BigDecimal quantity = position.getQuantity();

                BigDecimal profitRate = currentPrice.subtract(avgBuyPrice)
                        .divide(avgBuyPrice, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                boolean takeProfit = profitRate.compareTo(tradingProperties.getTakeProfitRate()) >= 0;
                boolean stopLoss = profitRate.compareTo(tradingProperties.getStopLossRate()) <= 0;

                if (takeProfit || stopLoss) {
                    var order = tradeExecutionService.sellLimit(market, quantity, currentPrice);

                    if (order != null) {
                        log.info("[포지션 청산] market={}, qty={}, price={}, profitRate={}, reason={}",
                                market,
                                quantity,
                                currentPrice,
                                profitRate,
                                takeProfit ? "목표수익률도달" : "손절");

                        tradingGuardService.markExited(market);
                    } else {
                        log.warn("[포지션 청산 실패] market={}", market);
                    }
                } else {
                    log.info("[포지션 유지] market={}, currentPrice={}, avgBuyPrice={}, profitRate={}",
                            market, currentPrice, avgBuyPrice, profitRate);
                }

            } catch (Exception e) {
                log.error("[포지션 모니터링 예외] positionId={}", position.getId(), e);
            }
        }
    }
}