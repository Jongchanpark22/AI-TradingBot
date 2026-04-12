package com.example.cryptobot.strategy.monitor;

import com.example.cryptobot.exchange.upbit.client.UpbitWebSocketClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.service.UpbitOrderService;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderRepository;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.risk.TrailingDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Real-time position monitor that listens to Upbit WebSocket ticks and
 * manages open positions: stop-loss, trailing stop, and partial exit.
 *
 * <p>This solves the critical gap where the 1-hour scheduler could miss a
 * flash crash. The monitor reacts to every trade tick (~100ms granularity)
 * and fires market sell orders immediately when a stop is breached.
 *
 * <h3>Flow</h3>
 * <pre>
 * ApplicationReadyEvent
 *   → load all OPEN positions from DB
 *   → subscribe to their symbols via WebSocket
 *   → on each tick:
 *       1. update highestPriceSinceEntry
 *       2. call RiskManager.updateTrailing()
 *       3. if shouldExitNow → market sell (full)
 *       4. if shouldPartialExit → market sell (50%)
 *       5. if trailing moved → update position in DB
 * </pre>
 */
@Slf4j
@Service
public class PositionMonitor {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final UpbitWebSocketClient webSocketClient;
    private final UpbitOrderService orderService;
    private final RiskManager riskManager;

    /** In-memory snapshot of monitored positions, keyed by symbol. */
    private final Map<String, MonitoredPosition> monitored = new ConcurrentHashMap<>();

    public PositionMonitor(
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            UpbitWebSocketClient webSocketClient,
            UpbitOrderService orderService) {
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.webSocketClient = webSocketClient;
        this.orderService = orderService;
        this.riskManager = new RiskManager(new RiskParameters(
                0.01, 1.5, 2.0, 1.0, 3.0, 0.05, 3));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        loadOpenPositions();

        if (monitored.isEmpty()) {
            log.info("No open positions to monitor");
            return;
        }

        webSocketClient.onTick(this::onTick);
        webSocketClient.start(monitored.keySet());
        log.info("PositionMonitor started, watching {} symbols: {}",
                monitored.size(), monitored.keySet());
    }

    /**
     * Called by HybridStrategyExecutor (or any entry point) after a new
     * position is opened, so the monitor picks it up immediately without
     * waiting for a restart.
     */
    public void trackPosition(Position position) {
        MonitoredPosition mp = MonitoredPosition.from(position);
        monitored.put(position.getSymbol(), mp);
        webSocketClient.addSymbols(List.of(position.getSymbol()));
        log.info("Now tracking {} — stop={}, TP={}",
                position.getSymbol(), mp.currentStop, mp.takeProfit);
    }

    /**
     * Remove a position from monitoring (after full exit).
     */
    public void untrackPosition(String symbol) {
        monitored.remove(symbol);
        if (!monitored.containsKey(symbol)) {
            webSocketClient.removeSymbols(List.of(symbol));
        }
        log.info("Stopped tracking {}", symbol);
    }

    // ---- tick handler (called on OkHttp thread) ----

    private void onTick(String symbol, BigDecimal price) {
        MonitoredPosition mp = monitored.get(symbol);
        if (mp == null) return;

        double currentPrice = price.doubleValue();

        // update highest seen
        if (currentPrice > mp.highestSeen) {
            mp.highestSeen = currentPrice;
        }

        TrailingDecision decision = riskManager.updateTrailing(
                mp.entryPrice,
                mp.initialStop,
                mp.currentStop,
                currentPrice,
                mp.highestSeen,
                mp.atr,
                mp.partialDone
        );

        if (decision.shouldExitNow()) {
            handleFullExit(mp, price, decision.reason());
        } else if (decision.shouldPartialExit()) {
            handlePartialExit(mp, price, decision.reason());
            mp.currentStop = decision.newStopLoss();
            mp.partialDone = true;
            persistPositionState(mp);
        } else if (decision.newStopLoss() > mp.currentStop) {
            mp.currentStop = decision.newStopLoss();
            persistPositionState(mp);
            log.debug("[{}] trailing stop → {}", mp.symbol, mp.currentStop);
        }
    }

    private void handleFullExit(MonitoredPosition mp, BigDecimal price, String reason) {
        log.warn("⚡ [{}] STOP HIT @ {} — {} — selling full position",
                mp.symbol, price, reason);
        try {
            UpbitOrderDto result = orderService.placeSellMarketOrder(mp.symbol, mp.quantity);
            if (result != null) {
                saveOrderRecord(mp, mp.quantity, price, result, "StopLoss:" + reason);
            } else {
                log.error("[PositionMonitor] 손절 주문 제출 실패: {}", mp.symbol);
            }
            closePositionInDb(mp.positionId);
            untrackPosition(mp.symbol);
        } catch (Exception e) {
            log.error("Failed to execute stop-loss sell for {}", mp.symbol, e);
        }
    }

    private void handlePartialExit(MonitoredPosition mp, BigDecimal price, String reason) {
        BigDecimal halfQty = mp.quantity.divide(BigDecimal.valueOf(2), 8, RoundingMode.DOWN);
        log.info("📊 [{}] PARTIAL EXIT @ {} — {} — selling {}",
                mp.symbol, price, reason, halfQty);
        try {
            UpbitOrderDto result = orderService.placeSellMarketOrder(mp.symbol, halfQty);
            if (result != null) {
                saveOrderRecord(mp, halfQty, price, result, "PartialExit:+1R");
            } else {
                log.error("[PositionMonitor] 부분 청산 주문 제출 실패: {}", mp.symbol);
            }
            mp.quantity = mp.quantity.subtract(halfQty);
            updatePositionPartialExit(mp);
        } catch (Exception e) {
            log.error("Failed to execute partial exit for {}", mp.symbol, e);
        }
    }

    private void saveOrderRecord(MonitoredPosition mp, BigDecimal quantity,
                                 BigDecimal price, UpbitOrderDto dto, String remark) {
        try {
            positionRepository.findById(mp.positionId).ifPresent(pos -> {
                BigDecimal executedVolume = dto.getExecutedVolume() != null ? dto.getExecutedVolume() : quantity;
                BigDecimal paidFee        = dto.getPaidFee()        != null ? dto.getPaidFee()        : BigDecimal.ZERO;
                Order.OrderStatus status  = "done".equals(dto.getState())
                        ? Order.OrderStatus.FILLED : Order.OrderStatus.PENDING;

                Order order = Order.builder()
                        .account(pos.getAccount())
                        .symbol(mp.symbol)
                        .type(Order.OrderType.MARKET)
                        .side(Order.OrderSide.SELL)
                        .price(price)
                        .quantity(quantity)
                        .filledQuantity(executedVolume)
                        .filledAmount(price.multiply(executedVolume))
                        .fee(paidFee)
                        .status(status)
                        .exchangeOrderId(dto.getUuid())
                        .totalAmount(price.multiply(quantity))
                        .remark(remark)
                        .build();

                orderRepository.save(order);
                log.info("[PositionMonitor] 거래 내역 저장: symbol={}, qty={}, status={}, uuid={}",
                        mp.symbol, quantity, status, dto.getUuid());
            });
        } catch (Exception e) {
            log.error("[PositionMonitor] 거래 내역 저장 실패: symbol={}", mp.symbol, e);
        }
    }

    // ---- DB persistence ----

    private void loadOpenPositions() {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        for (Position p : openPositions) {
            if (p.getCurrentStopLoss() == null || p.getAvgBuyPrice() == null) {
                log.warn("Skipping position {} — missing risk fields", p.getSymbol());
                continue;
            }
            monitored.put(p.getSymbol(), MonitoredPosition.from(p));
        }
        log.info("Loaded {} open positions for monitoring", monitored.size());
    }

    @Transactional
    protected void persistPositionState(MonitoredPosition mp) {
        positionRepository.findById(mp.positionId).ifPresent(p -> {
            p.setCurrentStopLoss(mp.currentStop);
            p.setHighestPriceSinceEntry(mp.highestSeen);
            p.setPartialExitDone(mp.partialDone);
            positionRepository.save(p);
        });
    }

    @Transactional
    protected void closePositionInDb(Long positionId) {
        positionRepository.findById(positionId).ifPresent(p -> {
            p.setStatus(Position.PositionStatus.CLOSED);
            positionRepository.save(p);
        });
    }

    @Transactional
    protected void updatePositionPartialExit(MonitoredPosition mp) {
        positionRepository.findById(mp.positionId).ifPresent(p -> {
            p.setQuantity(mp.quantity);
            p.setCurrentStopLoss(mp.currentStop);
            p.setHighestPriceSinceEntry(mp.highestSeen);
            p.setPartialExitDone(true);
            p.setStatus(Position.PositionStatus.PARTIAL);
            positionRepository.save(p);
        });
    }

    // ---- inner state class ----

    static class MonitoredPosition {
        final Long positionId;
        final String symbol;
        final double entryPrice;
        final double initialStop;
        final double takeProfit;
        final double atr;

        volatile double currentStop;
        volatile double highestSeen;
        volatile boolean partialDone;
        volatile BigDecimal quantity;

        MonitoredPosition(Long positionId, String symbol, double entryPrice,
                          double initialStop, double currentStop, double takeProfit,
                          double highestSeen, double atr, boolean partialDone,
                          BigDecimal quantity) {
            this.positionId = positionId;
            this.symbol = symbol;
            this.entryPrice = entryPrice;
            this.initialStop = initialStop;
            this.currentStop = currentStop;
            this.takeProfit = takeProfit;
            this.highestSeen = highestSeen;
            this.atr = atr;
            this.partialDone = partialDone;
            this.quantity = quantity;
        }

        static MonitoredPosition from(Position p) {
            double entry = p.getAvgBuyPrice() != null ? p.getAvgBuyPrice().doubleValue() : 0;
            double initStop = p.getInitialStopLoss() != null ? p.getInitialStopLoss() : 0;
            double curStop = p.getCurrentStopLoss() != null ? p.getCurrentStopLoss() : initStop;
            double tp = p.getTakeProfitPrice() != null ? p.getTakeProfitPrice() : 0;
            double highest = p.getHighestPriceSinceEntry() != null ? p.getHighestPriceSinceEntry() : entry;
            double atr = p.getAtrAtEntry() != null ? p.getAtrAtEntry() : 0;
            boolean partial = Boolean.TRUE.equals(p.getPartialExitDone());
            BigDecimal qty = p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO;

            return new MonitoredPosition(p.getId(), p.getSymbol(), entry,
                    initStop, curStop, tp, highest, atr, partial, qty);
        }
    }
}
