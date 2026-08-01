package com.example.cryptobot.holding.dto;

import com.example.cryptobot.holding.UserHolding;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 보유 종목 응답 DTO.
 * 현재가, 미실현 손익, 손익률을 포함합니다.
 */
@Getter
@Builder
public class HoldingResponse {

    private Long id;
    private String symbol;
    private BigDecimal avgBuyPrice;
    private BigDecimal quantity;
    private String memo;

    /** 현재가 (null: 시세 조회 불가) */
    private BigDecimal currentPrice;

    /** 미실현 손익 금액 = (현재가 - 평균매수가) × 수량 */
    private BigDecimal unrealizedPnl;

    /** 미실현 손익률 (%) */
    private BigDecimal unrealizedPnlRate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * UserHolding 엔티티 + 현재가로 응답 객체 생성.
     *
     * @param holding     보유 종목 엔티티
     * @param currentPrice 현재가 (null 허용)
     */
    public static HoldingResponse from(UserHolding holding, BigDecimal currentPrice) {
        BigDecimal pnl = null;
        BigDecimal pnlRate = null;

        if (currentPrice != null && holding.getAvgBuyPrice() != null
                && holding.getQuantity() != null
                && holding.getAvgBuyPrice().compareTo(BigDecimal.ZERO) > 0) {
            pnl = currentPrice.subtract(holding.getAvgBuyPrice())
                    .multiply(holding.getQuantity());
            pnlRate = currentPrice.subtract(holding.getAvgBuyPrice())
                    .divide(holding.getAvgBuyPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return HoldingResponse.builder()
                .id(holding.getId())
                .symbol(holding.getSymbol())
                .avgBuyPrice(holding.getAvgBuyPrice())
                .quantity(holding.getQuantity())
                .memo(holding.getMemo())
                .currentPrice(currentPrice)
                .unrealizedPnl(pnl)
                .unrealizedPnlRate(pnlRate)
                .createdAt(holding.getCreatedAt())
                .updatedAt(holding.getUpdatedAt())
                .build();
    }
}
