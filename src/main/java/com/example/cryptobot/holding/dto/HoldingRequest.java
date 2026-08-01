package com.example.cryptobot.holding.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 보유 종목 생성/수정 요청 DTO.
 */
@Getter
@Setter
public class HoldingRequest {

    /** 마켓 코드 (예: KRW-BTC) */
    private String symbol;

    /** 평균 매수가 */
    private BigDecimal avgBuyPrice;

    /** 보유 수량 */
    private BigDecimal quantity;

    /** 사용자 메모 */
    private String memo;
}
