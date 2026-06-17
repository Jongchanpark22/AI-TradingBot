package com.example.cryptobot.strategy.monitor;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.account.AccountService;
import com.example.cryptobot.exchange.upbit.client.UpbitWebSocketClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.service.UpbitAccountService;
import com.example.cryptobot.exchange.upbit.service.UpbitMarketService;
import com.example.cryptobot.exchange.upbit.service.UpbitOrderService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderRepository;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.risk.TrailingDecision;
import com.example.cryptobot.trade.TradeHistory;
import com.example.cryptobot.trade.TradeHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UpbitAccountService upbitAccountService;
    private final AccountService accountService;
    private final TradeHistoryService tradeHistoryService;
    private final RiskManager riskManager;

    private final UpbitMarketService upbitMarketService;
    private final CandleRepository candleRepository;

    /** In-memory snapshot of monitored positions, keyed by symbol. */
    private final Map<String, MonitoredPosition> monitored = new ConcurrentHashMap<>();

    public PositionMonitor(
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            UpbitWebSocketClient webSocketClient,
            UpbitOrderService orderService,
            UpbitAccountService upbitAccountService,
            AccountService accountService,
            TradeHistoryService tradeHistoryService,
            UpbitMarketService upbitMarketService,
            CandleRepository candleRepository) {
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.webSocketClient = webSocketClient;
        this.orderService = orderService;
        this.upbitAccountService = upbitAccountService;
        this.accountService = accountService;
        this.tradeHistoryService = tradeHistoryService;
        this.upbitMarketService = upbitMarketService;
        this.candleRepository = candleRepository;
        this.riskManager = new RiskManager(new RiskParameters(
                0.01, 1.5, 2.0, 2.0, 3.0, 0.05, 3));
        // partialExitRMultiple=2.0 → ATR 1.5배 손절 기준 +2R에서 부분청산 후 break-even 이동
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        // 업비트 실제 보유 코인과 DB 포지션 자동 싱크
        syncPositionsFromUpbit();

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
     * 업비트 실제 보유 코인을 조회하여 DB에 OPEN 포지션이 없는 경우 자동 생성.
     * 앱 재시작 시 또는 외부에서 직접 매수한 코인도 모니터링에 포함된다.
     */
    @Transactional
    protected void syncPositionsFromUpbit() {
        try {
            Account account = accountService.getPrimaryAccount();
            List<UpbitAccountDto> holdings = upbitAccountService.getAccounts();

            // 업비트에서 실제 보유 중인 심볼 목록 (KRW 제외, 잔고 > 0)
            Set<String> actualSymbols = holdings.stream()
                    .filter(h -> !"KRW".equalsIgnoreCase(h.getCurrency()))
                    .filter(h -> h.getBalance() != null && h.getBalance().compareTo(BigDecimal.ZERO) > 0)
                    .map(h -> "KRW-" + h.getCurrency().toUpperCase())
                    .collect(Collectors.toSet());

            // DB OPEN 포지션 중 업비트에 실제 없는 것 → CLOSED 처리
            List<Position> dbOpenPositions = positionRepository.findAllActive().stream()
                    .filter(p -> p.getAccount().getId().equals(account.getId()))
                    .toList();
            int closed = 0;
            for (Position p : dbOpenPositions) {
                if (!actualSymbols.contains(p.getSymbol())) {
                    p.setStatus(Position.PositionStatus.CLOSED);
                    positionRepository.save(p);
                    closed++;
                    log.info("[싱크] {} — 업비트 잔고 없음, CLOSED 처리", p.getSymbol());
                }
            }

            // 업비트에 있는데 DB에 없으면 → 생성
            int created = 0;
            for (UpbitAccountDto holding : holdings) {
                String currency = holding.getCurrency();
                if ("KRW".equalsIgnoreCase(currency)) continue;
                if (holding.getBalance() == null || holding.getBalance().compareTo(BigDecimal.ZERO) <= 0) continue;

                String symbol = "KRW-" + currency.toUpperCase();

                if (positionRepository.findActiveByAccountAndSymbol(account, symbol).isPresent()) {
                    log.debug("[싱크] {} — 기존 활성 포지션 존재, 스킵", symbol);
                    continue;
                }

                BigDecimal avgBuyPrice = holding.getAvgBuyPrice() != null
                        ? holding.getAvgBuyPrice() : BigDecimal.ZERO;
                BigDecimal quantity = holding.getBalance();

                if (avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[싱크] {} — 평균 매수가 없음, 스킵", symbol);
                    continue;
                }

                double entry = avgBuyPrice.doubleValue();
                double atr = fetchCurrentAtr(symbol);
                double stopLoss, takeProfit;
                if (atr > 0) {
                    double atrStop = entry - 2.5 * atr;  // 2.0→2.5: executeBuySignal과 일관성
                    // 안전 한도: ATR stop이 -10% 초과 내려가면 고정 5% 사용
                    stopLoss   = atrStop > entry * 0.90 ? atrStop : entry * (1.0 - DEFAULT_STOP_LOSS_RATE);
                    takeProfit = entry + 5.0 * atr;  // 4.0→5.0: 2:1 손익비 유지
                } else {
                    stopLoss   = entry * (1.0 - DEFAULT_STOP_LOSS_RATE);
                    takeProfit = entry * (1.0 + DEFAULT_TAKE_PROFIT_RATE);
                }

                Position position = Position.builder()
                        .account(account)
                        .symbol(symbol)
                        .quantity(quantity)
                        .avgBuyPrice(avgBuyPrice)
                        .currentPrice(avgBuyPrice)
                        .initialStopLoss(stopLoss)
                        .currentStopLoss(stopLoss)
                        .takeProfitPrice(takeProfit)
                        .highestPriceSinceEntry(entry)
                        .atrAtEntry(atr)
                        .status(Position.PositionStatus.OPEN)
                        .build();

                positionRepository.save(position);
                created++;
                log.info("[싱크] {} 포지션 생성 — 평단={}, 수량={}, ATR={}, 손절={}, 익절={}",
                        symbol, avgBuyPrice, quantity,
                        String.format("%.4f", atr),
                        String.format("%.2f", stopLoss),
                        String.format("%.2f", takeProfit));
            }

            log.info("[싱크] 업비트 잔고 싱크 완료 — 신규 생성 {} 개, CLOSED 처리 {} 개", created, closed);
        } catch (Exception e) {
            log.error("[싱크] 업비트 잔고 싱크 실패 — DB 포지션 기준으로 계속 진행", e);
        }
    }

    /**
     * Called by HybridStrategyExecutor (or any entry point) after a new
     * position is opened, so the monitor picks it up immediately without
     * waiting for a restart.
     */
    public void trackPosition(Position position) {
        MonitoredPosition mp = MonitoredPosition.from(position);
        monitored.put(position.getSymbol(), mp);

        if (!webSocketClient.isConnected()) {
            // WebSocket이 꺼져 있으면 새로 시작
            webSocketClient.onTick(this::onTick);
            webSocketClient.start(monitored.keySet());
        } else {
            webSocketClient.addSymbols(List.of(position.getSymbol()));
        }

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

        // ---- 목표 수익(7%) 최초 도달 — 즉시 매도 대신 추세 추적 모드로 전환 ----
        if (!mp.aboveProfitTarget) {
            double profitTarget = mp.takeProfit > 0
                    ? mp.takeProfit
                    : mp.entryPrice * (1.0 + PROFIT_TARGET_RATE);
            if (currentPrice >= profitTarget) {
                mp.aboveProfitTarget = true;
                persistPositionState(mp);
                log.info("🎯 [{}] 목표 수익 {}% 달성 @ {} — 상승 추세 추적 모드 전환 (최고점 대비 {}% 하락 시 매도)",
                        mp.symbol, (int)(PROFIT_TARGET_RATE * 100), price,
                        (int)(TRAILING_ABOVE_TARGET_PERCENT * 100));
            }
        }

        // ---- 목표 수익 달성 후: 타이트한 트레일링으로 상승 추세 추적 ----
        // 최고점 대비 TRAILING_ABOVE_TARGET_PERCENT(3%) 하락 시 하락 전환으로 판단 → 매도
        if (mp.aboveProfitTarget) {
            double tightStop = mp.highestSeen * (1.0 - TRAILING_ABOVE_TARGET_PERCENT);
            if (currentPrice <= tightStop) {
                handleFullExit(mp, price, String.format(
                        "목표 달성 후 하락 전환 — 최고점 %.2f 대비 %.1f%% 하락 (매도기준=%.2f)",
                        mp.highestSeen, TRAILING_ABOVE_TARGET_PERCENT * 100, tightStop));
            }
            // 목표 달성 구간은 일반 trailing 로직 적용 안 함 (타이트 트레일링으로만 관리)
            return;
        }

        // ---- 목표 미달 구간: chandelier trailing stop / 손절 로직 ----
        // 상승 도중 추세 반전 시(chandelier stop 터치) 자동 손절 처리
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
            // 실제 주문 가능 잔고 확인 (locked 잔고 제외)
            String currency = mp.symbol.replace("KRW-", "");
            BigDecimal available = upbitAccountService.getAvailableBalance(currency);

            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                // 가용 잔고 없음 — 미체결 주문에 잠겨있거나 이미 매도된 상태
                log.warn("[PositionMonitor] {} 가용 잔고 없음 (locked 또는 이미 매도) — 포지션 강제 CLOSED 처리", mp.symbol);
                closePositionInDb(mp.positionId);
                untrackPosition(mp.symbol);
                return;
            }

            // 가용 잔고가 포지션 수량보다 적으면 가용 잔고만큼만 매도
            BigDecimal sellQty = available.min(mp.quantity);
            log.info("[PositionMonitor] {} 매도 수량: position={}, available={}, 실제매도={}",
                    mp.symbol, mp.quantity, available, sellQty);

            UpbitOrderDto result = orderService.placeSellMarketOrder(mp.symbol, sellQty);
            if (result != null) {
                saveOrderRecord(mp, sellQty, price, result, "StopLoss:" + reason);
                saveTradeHistory(mp, price, sellQty, reason, false);
                closePositionInDb(mp.positionId);
                untrackPosition(mp.symbol);
            } else {
                log.error("[PositionMonitor] 매도 주문 제출 실패, 포지션 유지: {}", mp.symbol);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body.contains("insufficient_funds_ask") || body.contains("under_min_total_market_ask")) {
                log.warn("[PositionMonitor] {} 매도 불가 ({}) — 포지션 강제 CLOSED 처리", mp.symbol, body.contains("insufficient_funds_ask") ? "잔고부족" : "최소금액미달");
                closePositionInDb(mp.positionId);
                untrackPosition(mp.symbol);
            } else {
                log.error("Failed to execute stop-loss sell for {}", mp.symbol, e);
            }
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
                saveTradeHistory(mp, price, halfQty, reason, true);
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

    private void saveTradeHistory(MonitoredPosition mp, BigDecimal exitPrice,
                                   BigDecimal quantity, String reason, boolean partialExit) {
        try {
            positionRepository.findById(mp.positionId).ifPresent(pos -> {
                TradeHistory.ExitType exitType = resolveExitType(reason, partialExit);
                tradeHistoryService.record(
                        pos.getAccount(),
                        mp.symbol,
                        pos.getAvgBuyPrice(),
                        exitPrice,
                        quantity,
                        pos.getCreatedAt(),
                        exitType,
                        reason,
                        mp.atr,
                        mp.highestSeen,
                        partialExit);
            });
        } catch (Exception e) {
            log.error("[PositionMonitor] 거래 기록 저장 실패: symbol={}", mp.symbol, e);
        }
    }

    private TradeHistory.ExitType resolveExitType(String reason, boolean partialExit) {
        if (partialExit) return TradeHistory.ExitType.PARTIAL_EXIT;
        if (reason == null) return TradeHistory.ExitType.MANUAL;
        String lower = reason.toLowerCase();
        if (lower.contains("stop-loss") || lower.contains("stoplossprice") || lower.contains("손절"))
            return TradeHistory.ExitType.STOP_LOSS;
        if (lower.contains("trailing") || lower.contains("하락 전환"))
            return TradeHistory.ExitType.TRAILING_STOP;
        if (lower.contains("목표") || lower.contains("take"))
            return TradeHistory.ExitType.TAKE_PROFIT;
        return TradeHistory.ExitType.TRAILING_STOP;
    }

    // ---- DB persistence ----

    private static final double DEFAULT_STOP_LOSS_RATE         = 0.05;  // 5.0%
    private static final double DEFAULT_TAKE_PROFIT_RATE       = 0.10;  // 10.0%
    /** 10% 목표 달성 후 추세 추적 트레일링 폭 — 최고점 대비 이 비율 하락 시 매도 */
    private static final double TRAILING_ABOVE_TARGET_PERCENT  = 0.03;  // 3%: 수익 조기 확정
    /** 목표 수익 비율 — MonitoredPosition.from()에서 aboveProfitTarget 초기값 계산에 사용 */
    private static final double PROFIT_TARGET_RATE             = 0.10;  // 10%

    private void loadOpenPositions() {
        List<Position> openPositions = positionRepository.findAllActive();
        for (Position p : openPositions) {
            if (p.getAvgBuyPrice() == null) {
                log.warn("Skipping position {} — avgBuyPrice is null", p.getSymbol());
                continue;
            }
            // 리스크 필드가 없으면 avgBuyPrice 기준으로 기본값 계산 후 저장
            if (p.getCurrentStopLoss() == null || p.getInitialStopLoss() == null) {
                double entry = p.getAvgBuyPrice().doubleValue();
                double stopLoss   = entry * (1.0 - DEFAULT_STOP_LOSS_RATE);
                double takeProfit = entry * (1.0 + DEFAULT_TAKE_PROFIT_RATE);
                p.setInitialStopLoss(stopLoss);
                p.setCurrentStopLoss(stopLoss);
                p.setTakeProfitPrice(takeProfit);
                p.setHighestPriceSinceEntry(entry);
                positionRepository.save(p);
                log.warn("Position {} — risk fields 없음, 기본값 적용: 손절={}, 익절={}",
                        p.getSymbol(),
                        String.format("%.2f", stopLoss),
                        String.format("%.2f", takeProfit));
            }
            // atrAtEntry가 없는 포지션(재시작·sync 생성)은 실시간 재계산
            if (p.getAtrAtEntry() == null || p.getAtrAtEntry() == 0.0) {
                double atr = fetchCurrentAtr(p.getSymbol());
                if (atr > 0) {
                    p.setAtrAtEntry(atr);
                    positionRepository.save(p);
                    log.info("[ATR 복원] {} atrAtEntry 재계산: {}", p.getSymbol(), String.format("%.4f", atr));
                }
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

    // ---- ATR 헬퍼 ----

    /**
     * 15분봉 20개를 기준으로 ATR(14)을 실시간 계산.
     * 데이터 부족 또는 계산 실패 시 0.0 반환.
     */
    private double fetchCurrentAtr(String symbol) {
        try {
            upbitMarketService.getAndSaveCandles(symbol, 15, 20);
            List<Candle> candles = candleRepository
                    .findTopNBySymbolAndPeriodOrderByTimestampDesc(
                            symbol, Candle.CandlePeriod.FIFTEEN_MIN.name(), 20);
            if (candles == null || candles.size() < 14) return 0.0;
            double atr = Indicators.atr(candles, 14);
            return Double.isNaN(atr) ? 0.0 : atr;
        } catch (Exception e) {
            log.warn("[ATR] {} ATR 계산 실패: {}", symbol, e.getMessage());
            return 0.0;
        }
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
        /** true: 목표 수익(7%) 달성 후 추세 추적 모드 — 최고점 대비 3% 하락 시 매도 */
        volatile boolean aboveProfitTarget;

        MonitoredPosition(Long positionId, String symbol, double entryPrice,
                          double initialStop, double currentStop, double takeProfit,
                          double highestSeen, double atr, boolean partialDone,
                          BigDecimal quantity, boolean aboveProfitTarget) {
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
            this.aboveProfitTarget = aboveProfitTarget;
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
            // 재시작 시에도 최고점이 이미 7% 이상이었다면 추세 추적 모드로 복원
            boolean aboveTarget = entry > 0 && highest >= entry * (1.0 + PROFIT_TARGET_RATE);

            return new MonitoredPosition(p.getId(), p.getSymbol(), entry,
                    initStop, curStop, tp, highest, atr, partial, qty, aboveTarget);
        }
    }
}
