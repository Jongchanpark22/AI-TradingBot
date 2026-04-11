package com.example.cryptobot.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    public ResponseEntity<List<Order>> getOrdersBySymbol(
            @PathVariable Long accountId,
            @PathVariable String symbol
    ) {
        List<Order> orders = orderService.getOrdersBySymbol(accountId, symbol);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.getAccountId(),
                request.getSymbol(),
                request.getType(),
                request.getSide(),
                request.getPrice(),
                request.getQuantity()
        );
        return ResponseEntity.ok(order);
    }

    @Getter
    @Setter
    public static class CreateOrderRequest {
        private Long accountId;
        private String symbol;
        private Order.OrderType type;
        private Order.OrderSide side;
        private BigDecimal price;
        private BigDecimal quantity;
    }
}