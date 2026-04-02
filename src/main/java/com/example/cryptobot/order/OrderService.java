package com.example.cryptobot.order;

import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public Order createOrder(Long accountId, String symbol, Order.OrderType type,
                            Order.OrderSide side, Double price, Double quantity) {
        Order order = Order.builder()
                .account(new com.example.cryptobot.account.Account())
                .symbol(symbol)
                .type(type)
                .side(side)
                .price(price)
                .quantity(quantity)
                .filledQuantity(0.0)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(price * quantity)
                .filledAmount(0.0)
                .fee(0.0)
                .build();
        order.getAccount().setId(accountId);
        return orderRepository.save(order);
    }

    // Order 객체 직접 저장
    public Order createOrder(Order order) {
        if (order.getStatus() == null) {
            order.setStatus(Order.OrderStatus.PENDING);
        }
        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(0.0);
        }
        if (order.getFilledAmount() == null) {
            order.setFilledAmount(0.0);
        }
        if (order.getFee() == null) {
            order.setFee(0.0);
        }
        return orderRepository.save(order);
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    public List<Order> getOrdersByAccount(Long accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    public List<Order> getOrdersBySymbol(Long accountId, String symbol) {
        return orderRepository.findByAccountIdAndSymbol(accountId, symbol);
    }

    public List<Order> getTodayOrders(Long accountId) {
        return orderRepository.findByAccountIdAndCreatedAtAfter(accountId, LocalDateTime.now().minusHours(24));
    }

    public void updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = getOrder(orderId);
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Order status updated: orderId={}, status={}", orderId, status);
    }

    public void updateOrderFilled(Long orderId, Double filledQuantity, Double filledAmount) {
        Order order = getOrder(orderId);
        order.setFilledQuantity(filledQuantity);
        order.setFilledAmount(filledAmount);
        
        if (filledQuantity >= order.getQuantity()) {
            order.setStatus(Order.OrderStatus.FILLED);
        } else if (filledQuantity > 0) {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
        }
        
        orderRepository.save(order);
        log.info("Order filled: orderId={}, filledQuantity={}", orderId, filledQuantity);
    }

}
