package com.example.cryptobot.strategy.core;

import com.example.cryptobot.strategy.ai.AiSignalGate;
import com.example.cryptobot.strategy.ai.dto.AiPredictionResponse;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 전략 실행 로그 저장 서비스.
 *
 * <p>HybridStrategyExecutor에서 로그 직렬화/저장 로직을 분리하여
 * FeatureSnapshot JSON 변환 및 AI 예측값 저장을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyRunLogService {

    private final StrategyRunLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 전략 실행 결과를 저장한다. 진입 여부와 관계없이 항상 호출한다.
     *
     * @param signalId    신호 고유 ID (UUID)
     * @param snap        FeatureSnapshot (null이면 featureJson을 저장하지 않음)
     * @param aiDecision  AI 게이트 결정 (null이면 ML 필드를 NULL로 저장)
     * @param symbol      심볼
     * @param period      타임프레임 이름
     * @param ema12       EMA12
     * @param ema26       EMA26
     * @param sma50       SMA50
     * @param rsi         RSI
     * @param volumeRatio 거래량 비율
     * @param trend       추세 신호
     * @param momentum    모멘텀 신호
     * @param rsiSignal   RSI 신호
     * @param volumeSignal 거래량 신호
     * @param candleSignal 캔들 패턴 신호
     * @param finalSignal 최종 신호명 (필터 적용 후)
     * @param confidence  신뢰도
     * @param reason      신호 사유
     * @param orderCreated 주문 생성 여부
     * @param blockedReason 차단 사유 (없으면 null)
     */
    public void save(
            String signalId,
            FeatureSnapshot snap,
            AiSignalGate.Decision aiDecision,
            String symbol,
            String period,
            Double ema12,
            Double ema26,
            Double sma50,
            Double rsi,
            Double volumeRatio,
            HybridSignalAnalyzer.TrendSignal trend,
            HybridSignalAnalyzer.MomentumSignal momentum,
            HybridSignalAnalyzer.RSISignal rsiSignal,
            HybridSignalAnalyzer.VolumeSignal volumeSignal,
            HybridSignalAnalyzer.CandleSignal candleSignal,
            String finalSignal,
            Integer confidence,
            String reason,
            boolean orderCreated,
            String blockedReason) {

        // FeatureSnapshot → JSON 직렬화
        String featureJson = null;
        if (snap != null) {
            try {
                featureJson = objectMapper.writeValueAsString(snap);
            } catch (JsonProcessingException e) {
                log.warn("[StrategyRunLog] FeatureSnapshot 직렬화 실패: {}", e.getMessage());
            }
        }

        // AI 예측값 추출
        AiPredictionResponse prediction = aiDecision != null ? aiDecision.prediction() : null;
        Double mlBuyProb = prediction != null ? prediction.getBuyProbability() : null;
        String mlModelVer = prediction != null ? prediction.getModelVersion() : null;

        StrategyRunLog entity = StrategyRunLog.builder()
                .signalId(signalId)
                .featureJson(featureJson)
                .mlBuyProb(mlBuyProb)
                .mlModelVer(mlModelVer)
                .strategyName("AUTO_SCANNER")
                .symbol(symbol)
                .period(period)
                .ema12(ema12)
                .ema26(ema26)
                .sma50(sma50)
                .rsi(rsi)
                .volumeRatio(volumeRatio)
                .trendSignal(trend != null ? trend.name() : "UNKNOWN")
                .momentumSignal(momentum != null ? momentum.name() : "UNKNOWN")
                .rsiSignal(rsiSignal != null ? rsiSignal.name() : "UNKNOWN")
                .volumeSignal(volumeSignal != null ? volumeSignal.name() : "UNKNOWN")
                .candleSignal(candleSignal != null ? candleSignal.name() : "UNKNOWN")
                .finalSignal(finalSignal)
                .confidence(confidence)
                .reason(reason)
                .orderCreated(orderCreated)
                .blockedReason(blockedReason)
                .build();

        repository.save(entity);
    }
}
