package com.example.cryptobot.order;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public Order createOrder(Long accountId, String symbol, Order.OrderType type,
                             Order.OrderSide side, BigDecimal price, BigDecimal quantity) {

        Account account = new Account();
        account.setId(accountId);

        BigDecimal safePrice = price != null ? price : BigDecimal.ZERO;
        BigDecimal safeQuantity = quantity != null ? quantity : BigDecimal.ZERO;

        if (safePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_ORDER_PRICE", "주문 가격은 0보다 커야 합니다.");
        }
        if (safeQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_ORDER_QUANTITY", "주문 수량은 0보다 커야 합니다.");
        }

        Order order = Order.builder()
                .account(account)
                .symbol(symbol)
                .type(type)
                .side(side)
                .price(safePrice)
                .quantity(safeQuantity)
                .filledQuantity(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(safePrice.multiply(safeQuantity))
                .filledAmount(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .build();

        return orderRepository.save(order);
    }

    public Order createOrder(Order order) {
        if (order == null) {
            throw new BusinessException("ORDER_REQUIRED", "주문 정보가 필요합니다.");
        }

        if (order.getStatus() == null) {
            order.setStatus(Order.OrderStatus.PENDING);
        }
        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(BigDecimal.ZERO);
        }
        if (order.getFilledAmount() == null) {
            order.setFilledAmount(BigDecimal.ZERO);
        }
        if (order.getFee() == null) {
            order.setFee(BigDecimal.ZERO);
        }

        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_ORDER_PRICE", "주문 가격은 0보다 커야 합니다.");
        }
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_ORDER_QUANTITY", "주문 수량은 0보다 커야 합니다.");
        }

        // 항상 실제 값 기준으로 다시 계산
        order.setTotalAmount(order.getPrice().multiply(order.getQuantity()));

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByAccount(Long accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersBySymbol(Long accountId, String symbol) {
        return orderRepository.findByAccountIdAndSymbol(accountId, symbol);
    }

    @Transactional(readOnly = true)
    public List<Order> getTodayOrders(Long accountId) {
        return orderRepository.findByAccountIdAndCreatedAtAfter(
                accountId,
                LocalDateTime.now().minusHours(24)
        );
    }

    public void updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = getOrder(orderId);
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Order status updated: orderId={}, status={}", orderId, status);
    }

    public void updateOrderFilled(Long orderId, BigDecimal filledQuantity, BigDecimal filledAmount) {
        Order order = getOrder(orderId);

        BigDecimal safeFilledQuantity = filledQuantity != null ? filledQuantity : BigDecimal.ZERO;
        BigDecimal safeFilledAmount = filledAmount != null ? filledAmount : BigDecimal.ZERO;

        BigDecimal maxQuantity = order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO;
        if (safeFilledQuantity.compareTo(maxQuantity) > 0) {
            safeFilledQuantity = maxQuantity;
        }

        order.setFilledQuantity(safeFilledQuantity);
        order.setFilledAmount(safeFilledAmount);

        if (maxQuantity.compareTo(BigDecimal.ZERO) > 0) {
            if (safeFilledQuantity.compareTo(maxQuantity) >= 0) {
                order.setStatus(Order.OrderStatus.FILLED);
            } else if (safeFilledQuantity.compareTo(BigDecimal.ZERO) > 0) {
                order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            }
        }

        orderRepository.save(order);
        log.info("Order filled: orderId={}, filledQuantity={}", orderId, safeFilledQuantity);
    }
}