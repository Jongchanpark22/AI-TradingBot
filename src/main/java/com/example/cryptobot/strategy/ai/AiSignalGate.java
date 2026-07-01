package com.example.cryptobot.strategy.ai;

import com.example.cryptobot.strategy.ai.dto.AiPredictionResponse;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 신호 게이트 — 룰 기반 BUY 신호를 ML 레이어로 검증하여 최종 진입 여부를 결정한다.
 *
 * <h3>동작 모드 (trading.ai.mode)</h3>
 * <ul>
 *   <li><b>RULE_ONLY</b>: ML 호출 없음. 룰이 BUY이면 그대로 진입. 기존 동작과 100% 동일.</li>
 *   <li><b>SHADOW</b>: ML 호출하되 결과는 로그만 남기고 룰 결정 그대로 진입 (룰 vs ML 비교용).</li>
 *   <li><b>ENSEMBLE</b>: ML 확률 >= buy-threshold 인 경우에만 진입 허용. ML 서버 다운 시 룰 따름.</li>
 * </ul>
 *
 * <h3>불변식</h3>
 * <ul>
 *   <li>AI는 매수를 막을 뿐, 새로 만들지 않는다 — 룰이 BUY 아니면 항상 차단.</li>
 *   <li>ML 서버 다운 시 ENSEMBLE도 룰 단독으로 동작 (fail-safe).</li>
 * </ul>
 */
@Slf4j
@Component
public class AiSignalGate {

    public enum Mode { RULE_ONLY, SHADOW, ENSEMBLE }

    private final AiSignalClient client;
    private final Mode mode;
    private final double buyThreshold;

    public AiSignalGate(
            AiSignalClient client,
            @Value("${trading.ai.mode:RULE_ONLY}") String mode,
            @Value("${trading.ai.buy-threshold:0.6}") double buyThreshold) {
        this.client = client;
        this.mode = Mode.valueOf(mode.toUpperCase());
        this.buyThreshold = buyThreshold;
        log.info("[AI 게이트] 모드={}, 매수임계값={}", this.mode, buyThreshold);
    }

    /**
     * 룰이 BUY라고 판단했을 때, ML 레이어를 통해 진입 여부를 최종 결정한다.
     *
     * @param ruleSaysBuy 룰 기반 BUY 신호 여부
     * @param snap        현재 feature 스냅샷
     * @return Decision — enterAllowed: 진입 가능 여부, prediction: ML 예측(RULE_ONLY면 null)
     */
    public Decision evaluate(boolean ruleSaysBuy, FeatureSnapshot snap) {
        // 룰이 BUY 아니면 AI 판단 불필요 — 항상 차단
        if (!ruleSaysBuy) {
            return new Decision(false, null);
        }

        return switch (mode) {
            case RULE_ONLY -> new Decision(true, null);

            case SHADOW -> {
                AiPredictionResponse prediction = client.predict(snap);
                if (prediction != null) {
                    log.info("[AI/SHADOW] {} ML확률={} (모델={})",
                            snap.getSymbol(),
                            String.format("%.3f", prediction.getBuyProbability()),
                            prediction.getModelVersion());
                }
                // SHADOW: 룰대로 진입, ML은 로그만
                yield new Decision(true, prediction);
            }

            case ENSEMBLE -> {
                AiPredictionResponse prediction = client.predict(snap);
                if (prediction == null) {
                    // fail-safe: ML 서버 없으면 룰 단독으로 대체
                    log.warn("[AI/ENSEMBLE] {} ML 서버 응답 없음 — 룰 단독으로 대체", snap.getSymbol());
                    yield new Decision(true, null);
                }
                boolean allowed = prediction.getBuyProbability() >= buyThreshold;
                log.info("[AI/ENSEMBLE] {} ML확률={} threshold={} → {}",
                        snap.getSymbol(),
                        String.format("%.3f", prediction.getBuyProbability()),
                        buyThreshold,
                        allowed ? "진입허용" : "차단");
                yield new Decision(allowed, prediction);
            }
        };
    }

    /**
     * AI 게이트 결정 결과.
     *
     * @param enterAllowed 진입 허용 여부
     * @param prediction   ML 예측 결과 (RULE_ONLY 또는 ML 서버 다운 시 null)
     */
    public record Decision(boolean enterAllowed, AiPredictionResponse prediction) {}
}
