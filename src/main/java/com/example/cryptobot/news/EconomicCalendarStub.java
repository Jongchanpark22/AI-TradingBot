package com.example.cryptobot.news;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 경제 캘린더 Stub.
 * API 소스가 확정될 때까지 빈 구조를 유지합니다.
 * 연결할 API: 추후 ★상의 후 결정 (Investing.com RSS, FRED API 등)
 *
 * <p>반환 포맷 (확정 시 유지): [{event, date, actual, forecast, previous}]</p>
 */
@Slf4j
@Component
public class EconomicCalendarStub {

    /**
     * 경제 캘린더 이벤트 목록 반환 (현재는 빈 배열).
     * API 소스 확정 후 이 메서드를 실제 구현으로 교체합니다.
     */
    public List<Map<String, Object>> getUpcomingEvents() {
        log.debug("경제 캘린더 stub 호출 — 아직 미구현");
        return Collections.emptyList();
    }
}
