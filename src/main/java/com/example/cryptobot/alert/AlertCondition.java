package com.example.cryptobot.alert;

/**
 * 알림 조건 파싱 결과.
 * conditionJson → {"indicator":"RSI","op":"<","value":30}
 *
 * 지원 indicator: RSI, EMA12, EMA26, SMA50, PRICE, MACD, MACD_SIGNAL
 * 지원 op: <, >, <=, >=, ==
 */
public record AlertCondition(String indicator, String op, double value) {

    /**
     * 실제 지표값과 조건을 비교해 알림 발송 여부를 반환합니다.
     *
     * @param actualValue 현재 지표 계산값
     * @return true이면 조건 충족 → 알림 발송
     */
    public boolean isMet(double actualValue) {
        return switch (op) {
            case "<"  -> actualValue < value;
            case ">"  -> actualValue > value;
            case "<=" -> actualValue <= value;
            case ">=" -> actualValue >= value;
            case "==" -> Math.abs(actualValue - value) < 1e-9;
            default   -> false;
        };
    }
}
