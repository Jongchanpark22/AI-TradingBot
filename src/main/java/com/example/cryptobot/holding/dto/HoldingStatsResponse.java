package com.example.cryptobot.holding.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 보유 종목 과거 통계 응답 DTO.
 *
 * <p>⚠️ 면책: 이 데이터는 과거 거래 이력 기반의 참고 수치입니다.
 * 투자 권유가 아니며, 미래 수익을 보장하지 않습니다.
 * 표본이 적을수록 통계 신뢰도가 낮습니다.</p>
 */
@Getter
@Builder
public class HoldingStatsResponse {

    private String symbol;

    /** 총 거래 건수 (표본 수) */
    private long totalTrades;

    /** 수익 건수 (profitRate > 0) */
    private long profitableTrades;

    /** 손실 건수 */
    private long lossTrades;

    /** 최소 손익률 (%) */
    private Double minProfitRate;

    /** 최대 손익률 (%) */
    private Double maxProfitRate;

    /** 평균 손익률 (%) */
    private Double avgProfitRate;

    /** 면책 메시지 */
    @Builder.Default
    private String disclaimer = "과거 거래 이력 기반 참고 수치입니다. 투자 권유가 아니며 미래 수익을 보장하지 않습니다.";
}
