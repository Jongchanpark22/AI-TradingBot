package com.example.cryptobot.search;

/**
 * 종목 검색 결과 DTO.
 *
 * @param code      종목 코드 — 코인: Upbit 마켓 코드(KRW-BTC), 국내주식: DART corp_code(8자리)
 * @param name      종목 한국어 이름
 * @param type      종목 유형
 * @param stockCode 상장 종목코드 6자리 (국내주식·상장 종목만, 비상장이면 null)
 */
public record SearchResult(
        String code,
        String name,
        Type type,
        String stockCode
) {
    public enum Type {
        COIN,
        KR_STOCK
    }
}
