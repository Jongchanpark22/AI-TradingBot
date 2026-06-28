package com.example.cryptobot.strategy.ai;

import com.example.cryptobot.strategy.ai.dto.AiPredictionResponse;
import com.example.cryptobot.strategy.ai.dto.FeatureSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Python ML 서버 HTTP 클라이언트.
 *
 * <p>POST /predict 로 FeatureSnapshot을 전송하고 매수 확률을 수신한다.
 * ML 서버가 없거나 타임아웃/오류 발생 시 null 반환 — 봇은 룰 단독으로 계속 동작한다.
 */
@Slf4j
@Component
public class AiSignalClient {

    private final WebClient webClient;
    private final long timeoutMs;

    public AiSignalClient(
            @Qualifier("aiWebClient") WebClient webClient,
            @Value("${trading.ai.timeout-ms:800}") long timeoutMs) {
        this.webClient = webClient;
        this.timeoutMs = timeoutMs;
    }

    /**
     * ML 서버에 feature를 전송하고 매수 확률을 수신한다.
     *
     * @param snap 현재 기술적 지표 스냅샷
     * @return 예측 결과, ML 서버 없거나 오류 시 null
     */
    public AiPredictionResponse predict(FeatureSnapshot snap) {
        try {
            return webClient.post()
                    .uri("/predict")
                    .bodyValue(snap)
                    .retrieve()
                    .bodyToMono(AiPredictionResponse.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (Exception e) {
            log.debug("[AI] 예측 요청 실패 (ML 서버 없거나 타임아웃) — {}: {}", snap.getSymbol(), e.getMessage());
            return null;
        }
    }
}
