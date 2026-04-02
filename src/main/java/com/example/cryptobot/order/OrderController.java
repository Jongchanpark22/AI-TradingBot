package com.example.cryptobot.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Order>> getOrdersByAccount(@PathVariable Long accountId) {
        List<Order> orders = orderService.getOrdersByAccount(accountId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/account/{accountId}/symbol/{symbol}")
    public ResponseEntity<List<Order>> getOrdersBySymbol(@PathVariable Long accountId, @PathVariable String symbol) {
        List<Order> orders = orderService.getOrdersBySymbol(accountId, symbol);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.accountId,
                request.symbol,
                request.type,
                request.side,
                request.price,
                request.quantity
        );
        return ResponseEntity.ok(order);
    }

    public static class CreateOrderRequest {
        public Long accountId;
        public String symbol;
        public Order.OrderType type;
        public Order.OrderSide side;
        public Double price;
        public Double quantity;
    }

}

