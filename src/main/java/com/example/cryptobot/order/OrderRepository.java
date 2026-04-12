package com.example.cryptobot.order;

import com.example.cryptobot.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByAccountId(Long accountId);
    List<Order> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Order> findByAccountIdAndStatus(Long accountId, Order.OrderStatus status);
    List<Order> findByAccountIdAndCreatedAtAfter(Long accountId, LocalDateTime dateTime);
    Order findByExchangeOrderId(String exchangeOrderId);
    List<Order> findByAccountAndSymbolAndStatusIn(
            Account account,
            String symbol,
            List<Order.OrderStatus> statuses
    );

    List<Order> findByStatusInAndExchangeOrderIdIsNotNull(List<Order.OrderStatus> statuses);
}

