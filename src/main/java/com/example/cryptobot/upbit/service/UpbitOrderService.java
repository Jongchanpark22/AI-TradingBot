package com.example.cryptobot.upbit.service;

import com.example.cryptobot.upbit.client.UpbitApiClient;
import com.example.cryptobot.upbit.dto.UpbitOrderChanceDto;
import com.example.cryptobot.upbit.dto.UpbitOrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitOrderService {

    private final UpbitApiClient upbitApiClient;

    // ===== 주문 가능 정보 조회 =====

    @Transactional(readOnly = true)
    public UpbitOrderChanceDto getOrderChance(String market) {
        try {
            log.info("[업비트 주문가능조회 요청] market={}", market);

            UpbitOrderChanceDto result = upbitApiClient.getOrderChance(market);

            if (result != null) {
                log.info("[업비트 주문가능조회 성공] market={}, bidBalance={}, askBalance={}",
                        market,
                        result.getBidAccount() != null ? result.getBidAccount().getBalance() : null,
                        result.getAskAccount() != null ? result.getAskAccount().getBalance() : null
                );
                return result;
            }

            log.warn("[업비트 주문가능조회 실패] market={}, result=null", market);
            return null;
        } catch (Exception e) {
            log.error("[업비트 주문가능조회 예외] market={}", market, e);
            return null;
        }
    }

    // ===== 주문 생성 테스트 =====

    public UpbitOrderDto testBuyOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            log.info("[업비트 매수 테스트주문 요청] market={}, volume={}, price={}", market, volume, price);

            UpbitOrderDto result = upbitApiClient.createOrderTest(market, "bid", "limit", volume, price);

            if (result != null) {
                log.info("[업비트 매수 테스트주문 성공] market={}, uuid={}, state={}",
                        market, result.getUuid(), result.getState());
                return result;
            }

            log.warn("[업비트 매수 테스트주문 실패] market={}, volume={}, price={}, result=null",
                    market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("[업비트 매수 테스트주문 예외] market={}, volume={}, price={}",
                    market, volume, price, e);
            return null;
        }
    }

    public UpbitOrderDto testSellOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            log.info("[업비트 매도 테스트주문 요청] market={}, volume={}, price={}", market, volume, price);

            UpbitOrderDto result = upbitApiClient.createOrderTest(market, "ask", "limit", volume, price);

            if (result != null) {
                log.info("[업비트 매도 테스트주문 성공] market={}, uuid={}, state={}",
                        market, result.getUuid(), result.getState());
                return result;
            }

            log.warn("[업비트 매도 테스트주문 실패] market={}, volume={}, price={}, result=null",
                    market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("[업비트 매도 테스트주문 예외] market={}, volume={}, price={}",
                    market, volume, price, e);
            return null;
        }
    }

    // ===== 지정가 주문 =====

    public UpbitOrderDto placeBuyOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            log.info("[업비트 지정가 매수 요청] market={}, volume={}, price={}", market, volume, price);

            UpbitOrderDto order = upbitApiClient.createOrder(market, "bid", "limit", volume, price);

            if (order != null) {
                log.info("[업비트 지정가 매수 성공] market={}, uuid={}, state={}, volume={}, price={}",
                        market, order.getUuid(), order.getState(), volume, price);
                return order;
            }

            log.warn("[업비트 지정가 매수 실패] market={}, volume={}, price={}, result=null",
                    market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("[업비트 지정가 매수 예외] market={}, volume={}, price={}",
                    market, volume, price, e);
            return null;
        }
    }

    public UpbitOrderDto placeSellOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            log.info("[업비트 지정가 매도 요청] market={}, volume={}, price={}", market, volume, price);

            UpbitOrderDto order = upbitApiClient.createOrder(market, "ask", "limit", volume, price);

            if (order != null) {
                log.info("[업비트 지정가 매도 성공] market={}, uuid={}, state={}, volume={}, price={}",
                        market, order.getUuid(), order.getState(), volume, price);
                return order;
            }

            log.warn("[업비트 지정가 매도 실패] market={}, volume={}, price={}, result=null",
                    market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("[업비트 지정가 매도 예외] market={}, volume={}, price={}",
                    market, volume, price, e);
            return null;
        }
    }

    // ===== 시장가 주문 =====

    /**
     * 시장가 매수
     * 업비트는 시장가 매수 시 volume이 아니라 "주문 금액(price)"를 사용한다.
     * ord_type = price
     */
    public UpbitOrderDto placeBuyMarketOrder(String market, BigDecimal orderAmount) {
        try {
            log.info("[업비트 시장가 매수 요청] market={}, orderAmount={}", market, orderAmount);

            UpbitOrderDto order = upbitApiClient.createOrder(market, "bid", "price", null, orderAmount);

            if (order != null) {
                log.info("[업비트 시장가 매수 성공] market={}, uuid={}, state={}, orderAmount={}",
                        market, order.getUuid(), order.getState(), orderAmount);
                return order;
            }

            log.warn("[업비트 시장가 매수 실패] market={}, orderAmount={}, result=null", market, orderAmount);
            return null;
        } catch (Exception e) {
            log.error("[업비트 시장가 매수 예외] market={}, orderAmount={}", market, orderAmount, e);
            return null;
        }
    }

    /**
     * 주문 가능 금액 + 수수료 반영 시장가 매수
     */
    public UpbitOrderDto placeBuyMarketOrderWithFee(String market, BigDecimal krwBalance) {
        try {
            UpbitOrderChanceDto chance = getOrderChance(market);
            if (chance == null || chance.getBidFee() == null) {
                log.warn("[업비트 시장가 매수 실패] 주문 가능 정보 없음 market={}", market);
                return null;
            }

            BigDecimal feeRate = chance.getBidFee();
            BigDecimal orderAmount = krwBalance
                    .multiply(BigDecimal.ONE.subtract(feeRate))
                    .setScale(0, RoundingMode.DOWN);

            log.info("[업비트 시장가 매수 수수료 반영] market={}, krwBalance={}, feeRate={}, orderAmount={}",
                    market, krwBalance, feeRate, orderAmount);

            if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[업비트 시장가 매수 실패] 주문금액이 0 이하 market={}, orderAmount={}", market, orderAmount);
                return null;
            }

            return placeBuyMarketOrder(market, orderAmount);
        } catch (Exception e) {
            log.error("[업비트 시장가 매수(수수료반영) 예외] market={}, krwBalance={}", market, krwBalance, e);
            return null;
        }
    }

    /**
     * 시장가 매도
     * 업비트는 시장가 매도 시 volume 사용
     * ord_type = market
     */
    public UpbitOrderDto placeSellMarketOrder(String market, BigDecimal volume) {
        try {
            log.info("[업비트 시장가 매도 요청] market={}, volume={}", market, volume);

            UpbitOrderDto order = upbitApiClient.createOrder(market, "ask", "market", volume, null);

            if (order != null) {
                log.info("[업비트 시장가 매도 성공] market={}, uuid={}, state={}, volume={}",
                        market, order.getUuid(), order.getState(), volume);
                return order;
            }

            log.warn("[업비트 시장가 매도 실패] market={}, volume={}, result=null", market, volume);
            return null;
        } catch (Exception e) {
            log.error("[업비트 시장가 매도 예외] market={}, volume={}", market, volume, e);
            return null;
        }
    }

    // ===== 주문 취소 =====

    public UpbitOrderDto cancelOrder(String uuid) {
        try {
            log.info("[업비트 주문취소 요청] uuid={}", uuid);

            UpbitOrderDto order = upbitApiClient.cancelOrder(uuid);

            if (order != null) {
                log.info("[업비트 주문취소 성공] uuid={}, state={}", order.getUuid(), order.getState());
                return order;
            }

            log.warn("[업비트 주문취소 실패] uuid={}, result=null", uuid);
            return null;
        } catch (Exception e) {
            log.error("[업비트 주문취소 예외] uuid={}", uuid, e);
            return null;
        }
    }
}