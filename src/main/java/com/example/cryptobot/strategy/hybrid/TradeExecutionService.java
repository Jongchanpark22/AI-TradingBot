package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.upbit.service.UpbitOrderService;
import com.example.cryptobot.strategy.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutionService {

    private final UpbitOrderService upbitOrderService;
    private final TradingProperties tradingProperties;

    public UpbitOrderDto buyLimit(String market, BigDecimal volume, BigDecimal price) {
        if (isLiveMode()) {
            log.info("[실거래 매수] market={}, volume={}, price={}", market, volume, price);
            return upbitOrderService.placeBuyOrder(market, volume, price);
        }

        log.info("[테스트 매수] market={}, volume={}, price={}", market, volume, price);
        return upbitOrderService.testBuyOrder(market, volume, price);
    }

    public UpbitOrderDto sellLimit(String market, BigDecimal volume, BigDecimal price) {
        if (isLiveMode()) {
            log.info("[실거래 매도] market={}, volume={}, price={}", market, volume, price);
            return upbitOrderService.placeSellOrder(market, volume, price);
        }

        log.info("[테스트 매도] market={}, volume={}, price={}", market, volume, price);
        return upbitOrderService.testSellOrder(market, volume, price);
    }

    public boolean isLiveMode() {
        return "LIVE".equalsIgnoreCase(tradingProperties.getMode());
    }
}