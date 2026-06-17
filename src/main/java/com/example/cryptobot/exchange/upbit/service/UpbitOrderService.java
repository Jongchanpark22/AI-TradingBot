package com.example.cryptobot.exchange.upbit.service;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitOrderService {

    private final UpbitApiClient upbitApiClient;

    public UpbitOrderDto placeBuyOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "bid", "limit", volume, price);
            if (order != null) {
                log.info("✓ 매수 주문 성공: {} {} @{}", market, volume, price);
                return order;
            }
            log.warn("매수 주문 실패: {} {} @{}", market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("매수 주문 실패: {} {} @{}", market, volume, price, e);
            return null;
        }
    }

    public UpbitOrderDto placeSellOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "ask", "limit", volume, price);
            if (order != null) {
                log.info("✓ 매도 주문 성공: {} {} @{}", market, volume, price);
                return order;
            }
            log.warn("매도 주문 실패: {} {} @{}", market, volume, price);
            return null;
        } catch (Exception e) {
            log.error("매도 주문 실패: {} {} @{}", market, volume, price, e);
            return null;
        }
    }

    public UpbitOrderDto cancelOrder(String uuid) {
        try {
            UpbitOrderDto order = upbitApiClient.cancelOrder(uuid);
            if (order != null) {
                log.info("✓ 주문 취소 성공: {}", uuid);
                return order;
            }
            log.warn("주문 취소 실패: {}", uuid);
            return null;
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", uuid, e);
            return null;
        }
    }

    public UpbitOrderDto placeBuyMarketOrder(String market, BigDecimal volume) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "bid", "market", volume, null);
            if (order != null) {
                log.info("✓ 시장가 매수 주문 성공: {} {}", market, volume);
            }
            return order;
        } catch (Exception e) {
            log.error("시장가 매수 주문 실패: {} {}", market, volume, e);
            return null;
        }
    }

    public UpbitOrderDto placeSellMarketOrder(String market, BigDecimal volume) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "ask", "market", volume, null);
            if (order != null) {
                log.info("✓ 시장가 매도 주문 성공: {} {}", market, volume);
            }
            return order;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            // 잔고 부족 / 최소금액 미달 — 호출자가 포지션 강제 청산할 수 있도록 그대로 전파
            if (body.contains("insufficient_funds_ask") || body.contains("under_min_total_market_ask")) {
                throw e;
            }
            log.error("시장가 매도 주문 실패: {} {}", market, volume, e);
            return null;
        } catch (Exception e) {
            log.error("시장가 매도 주문 실패: {} {}", market, volume, e);
            return null;
        }
    }
}