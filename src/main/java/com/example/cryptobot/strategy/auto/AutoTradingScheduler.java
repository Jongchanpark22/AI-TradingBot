package com.example.cryptobot.strategy.auto;

import com.example.cryptobot.strategy.config.TradingProperties;
import com.example.cryptobot.strategy.dto.HybridDecisionResult;
import com.example.cryptobot.strategy.hybrid.TradeExecutionService;
import com.example.cryptobot.market.MarketUniverseService;
import com.example.cryptobot.upbit.dto.UpbitTickerDto;
import com.example.cryptobot.upbit.service.UpbitMarketService;
import com.example.cryptobot.upbit.service.UpbitOrderService;
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
public class AutoTradingScheduler {

    private final MarketUniverseService marketUniverseService;
    private final HybridStrategyService hybridStrategyService;
    private final UpbitMarketService upbitMarketService;
    private final UpbitOrderService upbitOrderService;
    private final TradeExecutionService tradeExecutionService;
    private final TradingProperties tradingProperties;
    private final TradingGuardService tradingGuardService;

    @Scheduled(fixedDelay = 60000)
    public void executeAutoBuyCycle() {
        try {
            List<String> topMarkets = marketUniverseService.getTopTradableMarkets(
                    tradingProperties.getTopCoinCount()
            );

            for (String market : topMarkets) {
                if (!tradingGuardService.canEnter(market)) {
                    continue;
                }

                HybridDecisionResult hybrid = hybridStrategyService.evaluate(market);

                if (hybrid == null || !hybrid.isTradable()) {
                    log.info("[자동매매] 진입 제외 market={}, reason={}",
                            market, hybrid != null ? hybrid.getReason() : "result null");
                    continue;
                }

                if (!"STRONG_BUY".equals(hybrid.getSignal()) && !"BUY".equals(hybrid.getSignal())) {
                    log.info("[자동매매] 매수 시그널 아님 market={}, signal={}", market, hybrid.getSignal());
                    continue;
                }

                var chance = upbitOrderService.getOrderChance(market);
                if (chance == null || chance.getBidAccount() == null) {
                    log.warn("[자동매매] 주문 가능 정보 없음 market={}", market);
                    continue;
                }

                BigDecimal bidBalance = chance.getBidAccount().getBalance();
                BigDecimal orderAmount = tradingProperties.getOrderAmount();

                if (bidBalance.compareTo(orderAmount) < 0) {
                    log.warn("[자동매매] 잔고 부족 market={}, bidBalance={}, orderAmount={}",
                            market, bidBalance, orderAmount);
                    continue;
                }

                UpbitTickerDto ticker = upbitMarketService.getTicker(market);
                if (ticker == null || ticker.getTradePrice() == null) {
                    log.warn("[자동매매] 현재가 조회 실패 market={}", market);
                    continue;
                }

                BigDecimal currentPrice = ticker.getTradePrice();
                BigDecimal volume = orderAmount.divide(currentPrice, 8, RoundingMode.DOWN);

                if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[자동매매] 계산된 수량이 0 이하 market={}", market);
                    continue;
                }

                var order = tradeExecutionService.buyLimit(market, volume, currentPrice);

                if (order == null) {
                    log.warn("[자동매매] 주문 생성 실패 market={}", market);
                    continue;
                }

                tradingGuardService.markEntered(market);
                log.info("[자동매매] 매수 성공 market={}, volume={}, price={}, mode={}",
                        market, volume, currentPrice, tradingProperties.getMode());

                break;
            }

        } catch (Exception e) {
            log.error("[자동매매] 매수 사이클 예외", e);
        }
    }

    private BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }
}