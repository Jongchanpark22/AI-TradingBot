package com.example.cryptobot.holding.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 관심 종목 생성 요청 DTO.
 */
@Getter
@Setter
public class WatchlistRequest {

    /** 마켓 코드 (예: KRW-BTC) */
    private String symbol;

    /** 사용자 메모 */
    private String memo;
}
