package com.example.cryptobot.exchange.upbit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Upbit WebSocket client for real-time trade (체결) data.
 *
 * <p>Connects to {@code wss://api.upbit.com/websocket/v1} and subscribes to
 * the {@code trade} type for the requested symbols. Each trade tick fires the
 * registered listener with (symbol, price).
 *
 * <p>Features:
 * <ul>
 *   <li>Auto-reconnect with exponential back-off (1s → 2s → 4s … cap 60s)</li>
 *   <li>Ping every 30s to keep the connection alive</li>
 *   <li>Dynamic symbol subscription — add/remove symbols at runtime</li>
 *   <li>Thread-safe: listener calls are dispatched on the OkHttp callback thread</li>
 * </ul>
 */
@Slf4j
@Component
public class UpbitWebSocketClient {

    private static final String WS_URL = "wss://api.upbit.com/websocket/v1";
    private static final long PING_INTERVAL_MS = 30_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private volatile WebSocket webSocket;
    private volatile BiConsumer<String, BigDecimal> tickListener;
    private ScheduledExecutorService scheduler;

    public UpbitWebSocketClient() {
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Register a listener that receives (symbol, tradePrice) on every tick.
     * Must be set before {@link #start}.
     */
    public void onTick(BiConsumer<String, BigDecimal> listener) {
        this.tickListener = listener;
    }

    /**
     * Start the WebSocket connection and subscribe to the given symbols.
     */
    public void start(Collection<String> symbols) {
        if (!running.compareAndSet(false, true)) {
            log.warn("WebSocket already running");
            return;
        }
        subscribedSymbols.addAll(symbols);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "upbit-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
        connect();
    }

    /**
     * Add symbols to the subscription. If already connected, reconnects with
     * the updated symbol set.
     */
    public void addSymbols(Collection<String> symbols) {
        if (subscribedSymbols.addAll(symbols) && webSocket != null) {
            sendSubscription();
        }
    }

    /**
     * Remove symbols from the subscription.
     */
    public void removeSymbols(Collection<String> symbols) {
        if (subscribedSymbols.removeAll(symbols) && webSocket != null) {
            if (subscribedSymbols.isEmpty()) {
                stop();
            } else {
                sendSubscription();
            }
        }
    }

    public Set<String> subscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    public boolean isConnected() {
        return webSocket != null && running.get();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (webSocket != null) {
            webSocket.close(1000, "shutdown");
            webSocket = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        log.info("Upbit WebSocket stopped");
    }

    // ---- internals ----

    private void connect() {
        if (!running.get() || subscribedSymbols.isEmpty()) return;

        Request request = new Request.Builder().url(WS_URL).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("Upbit WebSocket connected, subscribing to {} symbols", subscribedSymbols.size());
                reconnectAttempts.set(0);
                sendSubscription();
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                handleMessage(bytes.utf8());
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                log.info("Upbit WebSocket closing: {} {}", code, reason);
                ws.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                log.info("Upbit WebSocket closed: {} {}", code, reason);
                webSocket = null;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                log.error("Upbit WebSocket failure: {}", t.getMessage());
                webSocket = null;
                scheduleReconnect();
            }
        });
    }

    private void sendSubscription() {
        if (webSocket == null || subscribedSymbols.isEmpty()) return;
        try {
            // Upbit WebSocket subscription format:
            // [{"ticket":"unique-id"},{"type":"trade","codes":["KRW-BTC","KRW-ETH"]}]
            String ticket = UUID.randomUUID().toString().substring(0, 8);
            String codes = String.join("\",\"", subscribedSymbols);
            String message = String.format(
                    "[{\"ticket\":\"%s\"},{\"type\":\"trade\",\"codes\":[\"%s\"],\"isOnlyRealtime\":true}]",
                    ticket, codes);
            webSocket.send(message);
            log.info("Subscribed to trade ticks: {}", subscribedSymbols);
        } catch (Exception e) {
            log.error("Failed to send subscription", e);
        }
    }

    private void handleMessage(String json) {
        if (tickListener == null) return;
        try {
            JsonNode node = objectMapper.readTree(json);
            String type = node.path("type").asText("");
            if (!"trade".equals(type)) return;

            String symbol = node.path("code").asText();
            BigDecimal price = new BigDecimal(node.path("trade_price").asText("0"));

            if (!symbol.isEmpty() && price.signum() > 0) {
                tickListener.accept(symbol, price);
            }
        } catch (Exception e) {
            log.debug("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    private void scheduleReconnect() {
        if (!running.get() || scheduler == null || scheduler.isShutdown()) return;

        int attempt = reconnectAttempts.incrementAndGet();
        long delayMs = Math.min(1000L * (1L << Math.min(attempt - 1, 6)), MAX_BACKOFF_MS);
        log.info("Reconnecting in {}ms (attempt {})", delayMs, attempt);

        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }
}
