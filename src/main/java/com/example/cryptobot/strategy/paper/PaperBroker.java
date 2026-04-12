package com.example.cryptobot.strategy.paper;

import com.example.cryptobot.order.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory broker for paper-trading mode.
 *
 * <p>Paper-trading sits between back-testing and a real exchange:
 * <ul>
 *   <li>back-test = historical data, virtual fills</li>
 *   <li><b>paper-trading = live data, virtual fills</b></li>
 *   <li>live = live data, real fills</li>
 * </ul>
 *
 * <p>The point is to validate the production code path — order routing, slot
 * accounting, stop management — against a live tick stream without putting
 * real money at risk. Bugs that only show up under live latency, partial
 * candle updates, or socket reconnects get a chance to bite here instead of in
 * production.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Cash and per-symbol holdings are tracked as {@link BigDecimal} to
 *       match the production order entities. Indicators stay in {@code double}
 *       only inside the back-test/strategy layer.</li>
 *   <li>{@code MARKET} orders fill immediately at the supplied
 *       {@code referencePrice}; {@code LIMIT} buy orders fill when the
 *       reference price is at or below the limit, sells when it is at or
 *       above. This is the same convention the back-test engine uses.</li>
 *   <li>Available cash is debited on a buy fill and credited on a sell fill
 *       — there is no concept of margin or leverage. Simple by design.</li>
 *   <li>{@link #cancel(String)} removes a still-pending limit order. Used by
 *       the production caller when superseding an outstanding stop.</li>
 *   <li>The broker is single-threaded and not safe for concurrent callers.
 *       Wrap externally if used from multiple symbols.</li>
 * </ul>
 */
public final class PaperBroker {

    private final BigDecimal startingCash;
    private BigDecimal cash;
    private final Map<String, BigDecimal> holdings = new HashMap<>();
    private final Map<String, Order> openOrders = new HashMap<>();
    private final List<Order> filledOrders = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public PaperBroker(BigDecimal startingCash) {
        if (startingCash == null || startingCash.signum() <= 0) {
            throw new IllegalArgumentException("startingCash must be > 0");
        }
        this.startingCash = startingCash;
        this.cash = startingCash;
    }

    // ============================================================
    // Order entry
    // ============================================================

    /**
     * Submit an order. The {@code order} parameter is mutated in place: it is
     * assigned an exchange-style id and a {@code PENDING} status, and either
     * stored as open or filled immediately depending on type/price.
     *
     * @param order            order to submit
     * @param referencePrice   latest market price for the symbol; used for
     *                         immediate-fill checks
     * @return the same {@code order} reference for fluent chaining
     */
    public Order submit(Order order, BigDecimal referencePrice) {
        if (order == null) throw new IllegalArgumentException("order required");
        if (order.getSymbol() == null || order.getSymbol().isBlank())
            throw new IllegalArgumentException("symbol required");
        if (order.getQuantity() == null || order.getQuantity().signum() <= 0)
            throw new IllegalArgumentException("quantity must be > 0");
        if (referencePrice == null || referencePrice.signum() <= 0)
            throw new IllegalArgumentException("referencePrice must be > 0");

        order.setId(idSeq.getAndIncrement());
        order.setExchangeOrderId("PAPER-" + UUID.randomUUID());
        order.setStatus(Order.OrderStatus.PENDING);
        if (order.getFilledQuantity() == null) order.setFilledQuantity(BigDecimal.ZERO);
        if (order.getFilledAmount() == null) order.setFilledAmount(BigDecimal.ZERO);
        if (order.getFee() == null) order.setFee(BigDecimal.ZERO);

        if (order.getType() == Order.OrderType.MARKET) {
            // BUY pays the ask, SELL hits the bid — use reference price both
            // ways. Realism (spread) is not modelled in paper mode.
            fill(order, referencePrice);
        } else {
            tryFillLimit(order, referencePrice);
        }
        return order;
    }

    /**
     * Re-evaluate every open limit order against the latest reference price.
     * Call this on every tick / candle close so resting orders fire as soon as
     * the market crosses them.
     */
    public List<Order> onTick(String symbol, BigDecimal referencePrice) {
        if (symbol == null || referencePrice == null || referencePrice.signum() <= 0) {
            return List.of();
        }
        List<Order> justFilled = new ArrayList<>();
        // copy keys to avoid concurrent modification
        for (String id : List.copyOf(openOrders.keySet())) {
            Order o = openOrders.get(id);
            if (o == null || !symbol.equals(o.getSymbol())) continue;
            if (tryFillLimit(o, referencePrice)) {
                justFilled.add(o);
            }
        }
        return justFilled;
    }

    /** Cancel a still-open limit order by exchange id. Returns true if removed. */
    public boolean cancel(String exchangeOrderId) {
        Order o = openOrders.remove(exchangeOrderId);
        if (o == null) return false;
        o.setStatus(Order.OrderStatus.CANCELLED);
        return true;
    }

    // ============================================================
    // Account snapshots
    // ============================================================

    public BigDecimal cash() { return cash; }
    public BigDecimal startingCash() { return startingCash; }

    public BigDecimal holding(String symbol) {
        return holdings.getOrDefault(symbol, BigDecimal.ZERO);
    }

    /** Mark-to-market equity given the latest reference prices per symbol. */
    public BigDecimal equity(Map<String, BigDecimal> referencePrices) {
        BigDecimal total = cash;
        for (Map.Entry<String, BigDecimal> e : holdings.entrySet()) {
            BigDecimal qty = e.getValue();
            if (qty.signum() == 0) continue;
            BigDecimal px = referencePrices == null ? null : referencePrices.get(e.getKey());
            if (px == null) continue;
            total = total.add(px.multiply(qty));
        }
        return total;
    }

    public List<Order> filledOrders() {
        return List.copyOf(filledOrders);
    }

    public List<Order> openOrders() {
        return List.copyOf(openOrders.values());
    }

    // ============================================================
    // Internals
    // ============================================================

    private boolean tryFillLimit(Order order, BigDecimal referencePrice) {
        BigDecimal limit = order.getPrice();
        if (limit == null || limit.signum() <= 0) return false;

        boolean fillable = order.getSide() == Order.OrderSide.BUY
                ? referencePrice.compareTo(limit) <= 0
                : referencePrice.compareTo(limit) >= 0;

        if (!fillable) {
            // park as open
            openOrders.put(order.getExchangeOrderId(), order);
            return false;
        }
        openOrders.remove(order.getExchangeOrderId());
        // Limit fills go at the limit price by convention; the engine that
        // routes the order is responsible for picking a sane limit so the
        // average paper fill matches what we'd expect on the live exchange.
        fill(order, limit);
        return true;
    }

    private void fill(Order order, BigDecimal fillPrice) {
        BigDecimal qty = order.getQuantity();
        BigDecimal notional = fillPrice.multiply(qty);

        if (order.getSide() == Order.OrderSide.BUY) {
            if (cash.compareTo(notional) < 0) {
                order.setStatus(Order.OrderStatus.REJECTED);
                order.setRemark((order.getRemark() == null ? "" : order.getRemark() + "; ")
                        + "insufficient cash for fill");
                return;
            }
            cash = cash.subtract(notional);
            holdings.merge(order.getSymbol(), qty, BigDecimal::add);
        } else {
            BigDecimal have = holdings.getOrDefault(order.getSymbol(), BigDecimal.ZERO);
            if (have.compareTo(qty) < 0) {
                order.setStatus(Order.OrderStatus.REJECTED);
                order.setRemark((order.getRemark() == null ? "" : order.getRemark() + "; ")
                        + "insufficient holdings for sell");
                return;
            }
            BigDecimal remaining = have.subtract(qty);
            if (remaining.signum() == 0) holdings.remove(order.getSymbol());
            else holdings.put(order.getSymbol(), remaining);
            cash = cash.add(notional);
        }

        order.setStatus(Order.OrderStatus.FILLED);
        order.setFilledQuantity(qty);
        order.setFilledAmount(notional.setScale(2, RoundingMode.HALF_UP));
        order.setPrice(fillPrice);
        order.setTotalAmount(notional.setScale(2, RoundingMode.HALF_UP));
        order.setRemark((order.getRemark() == null ? "" : order.getRemark() + "; ")
                + "paper fill @ " + Instant.now());
        filledOrders.add(order);
    }
}
