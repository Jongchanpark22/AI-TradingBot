package com.example.cryptobot.alert;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.strategy.hybrid.TechnicalIndicatorCalculator;
import com.example.cryptobot.strategy.indicator.Indicators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 커스텀 알림 조건 평가 서비스.
 *
 * <p>15분마다 활성 알림을 순회하며 지표를 계산하고 조건 충족 여부를 평가합니다.
 * 조건 충족 시 현재는 로그로 기록하며, 추후 푸시 알림으로 확장합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private static final int CANDLE_LIMIT = 50;

    private final UserAlertRepository alertRepository;
    private final CandleRepository candleRepository;
    private final UserAlertService alertService;

    /**
     * 15분마다 활성 알림 조건 평가.
     * HybridStrategyExecutor 스케줄러와 동일 주기 — 지표가 방금 갱신된 직후 평가.
     */
    @Scheduled(cron = "0 5/15 * * * *")
    public void evaluateAll() {
        List<UserAlert> activeAlerts = alertRepository.findAllByEnabledTrue();
        if (activeAlerts.isEmpty()) return;

        log.debug("[알림평가] 활성 알림 {}건 평가 시작", activeAlerts.size());

        for (UserAlert alert : activeAlerts) {
            try {
                evaluate(alert);
            } catch (Exception e) {
                log.warn("[알림평가] 평가 실패: id={}, symbol={}", alert.getId(), alert.getSymbol(), e);
            }
        }
    }

    private void evaluate(UserAlert alert) {
        AlertCondition condition = alertService.parseCondition(alert.getConditionJson());
        if (condition == null) return;

        // 쿨다운 체크: 마지막 발송 후 cooldownMinutes 이내이면 스킵
        if (alert.getLastFiredAt() != null
                && alert.getLastFiredAt().plusMinutes(alert.getCooldownMinutes()).isAfter(LocalDateTime.now())) {
            return;
        }

        // DB에서 최신 캔들 조회
        List<Candle> candles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(
                        alert.getSymbol(), Candle.CandlePeriod.FIFTEEN_MIN.name(), CANDLE_LIMIT);

        if (candles == null || candles.size() < 14) {
            log.debug("[알림평가] 캔들 부족: symbol={}", alert.getSymbol());
            return;
        }

        candles.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        double actualValue = resolveIndicatorValue(condition.indicator(), candles);
        if (Double.isNaN(actualValue)) return;

        if (condition.isMet(actualValue)) {
            log.info("🔔 [알림] id={} name='{}' symbol={} — {} {} {} (실제값: {})",
                    alert.getId(), alert.getName(), alert.getSymbol(),
                    condition.indicator(), condition.op(), condition.value(), String.format("%.4f", actualValue));
            alert.setLastFiredAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }

    /**
     * indicator 이름으로 현재 지표값을 계산해 반환합니다.
     * 지원: RSI, EMA12, EMA26, SMA50, PRICE, MACD, MACD_SIGNAL, ATR, VOLUME_RATIO
     */
    private double resolveIndicatorValue(String indicator, List<Candle> candles) {
        List<Double> closes = candles.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        return switch (indicator.toUpperCase()) {
            case "RSI"          -> TechnicalIndicatorCalculator.calculateRSI(closes, 14);
            case "EMA12"        -> TechnicalIndicatorCalculator.calculateEMA(closes, 12);
            case "EMA26"        -> TechnicalIndicatorCalculator.calculateEMA(closes, 26);
            case "SMA50"        -> TechnicalIndicatorCalculator.calculateSMA(closes, 50);
            case "PRICE"        -> closes.get(closes.size() - 1);
            case "MACD"         -> {
                TechnicalIndicatorCalculator.MACDValues mv = TechnicalIndicatorCalculator.calculateMACD(closes);
                yield mv != null ? mv.getMacd() : Double.NaN;
            }
            case "MACD_SIGNAL"  -> {
                TechnicalIndicatorCalculator.MACDValues mv = TechnicalIndicatorCalculator.calculateMACD(closes);
                yield mv != null ? mv.getSignalLine() : Double.NaN;
            }
            case "ATR"          -> Indicators.atr(candles, 14);
            default             -> {
                log.warn("[알림평가] 지원하지 않는 indicator: {}", indicator);
                yield Double.NaN;
            }
        };
    }

    /**
     * 특정 심볼의 현재 지표 스냅샷을 반환합니다. (CoinChartController에서 사용)
     *
     * @return indicator → value 맵
     */
    public Map<String, Double> getIndicatorSnapshot(String symbol) {
        List<Candle> candles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(
                        symbol, Candle.CandlePeriod.FIFTEEN_MIN.name(), CANDLE_LIMIT);

        if (candles == null || candles.size() < 14) {
            return Map.of();
        }
        candles.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        List<Double> closes = candles.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        TechnicalIndicatorCalculator.MACDValues macd = TechnicalIndicatorCalculator.calculateMACD(closes);
        TechnicalIndicatorCalculator.BollingerBands bb =
                TechnicalIndicatorCalculator.calculateBollingerBands(closes, 20);

        double price = closes.get(closes.size() - 1);
        double atr = Indicators.atr(candles, 14);

        return Map.ofEntries(
                Map.entry("price",       price),
                Map.entry("rsi",         TechnicalIndicatorCalculator.calculateRSI(closes, 14)),
                Map.entry("ema12",       TechnicalIndicatorCalculator.calculateEMA(closes, 12)),
                Map.entry("ema26",       TechnicalIndicatorCalculator.calculateEMA(closes, 26)),
                Map.entry("sma50",       TechnicalIndicatorCalculator.calculateSMA(closes, 50)),
                Map.entry("macd",        macd != null ? macd.getMacd() : Double.NaN),
                Map.entry("macdSignal",  macd != null ? macd.getSignalLine() : Double.NaN),
                Map.entry("macdHist",    macd != null ? macd.getHistogram() : Double.NaN),
                Map.entry("bbUpper",     bb != null ? bb.getUpper() : Double.NaN),
                Map.entry("bbLower",     bb != null ? bb.getLower() : Double.NaN),
                Map.entry("bbMiddle",    bb != null ? bb.getMiddle() : Double.NaN),
                Map.entry("atr",         atr)
        );
    }
}
