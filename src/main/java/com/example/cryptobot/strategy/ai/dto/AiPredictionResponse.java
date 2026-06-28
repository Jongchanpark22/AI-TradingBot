package com.example.cryptobot.strategy.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python ML 서버 /predict 응답 DTO.
 *
 * <p>Python 서버가 반환하는 snake_case JSON을 camelCase 필드로 매핑한다.
 * ML 서버가 없거나 오류 시 null이 반환되므로 항상 null 체크 필요.
 */
@Data
@NoArgsConstructor
public class AiPredictionResponse {

    /** 매수 확률 (0.0 ~ 1.0). AiSignalGate에서 buy-threshold와 비교한다. */
    @JsonProperty("buy_probability")
    private double buyProbability;

    /** 추론에 사용된 모델 버전 (예: "v1.0.0"). 로그 추적용. */
    @JsonProperty("model_version")
    private String modelVersion;
}
