package com.example.cryptobot.home.dto;

import com.example.cryptobot.alert.UserAlert;
import com.example.cryptobot.holding.dto.HoldingResponse;
import com.example.cryptobot.news.NewsItem;

import java.util.List;

/**
 * 홈 브리핑 응답 DTO.
 *
 * <p>인사·지수 요약·보유 종목·최근 알림·뉴스를 한 번에 반환합니다.</p>
 */
public record HomeResponse(
        /** 인사 메시지 (예: "안녕하세요, 박종찬님!") */
        String greeting,

        /** 주요 지수 요약 */
        IndicesSnapshot indices,

        /** 내 보유 종목 목록 (현재가·미실현 손익 포함) */
        List<HoldingResponse> holdings,

        /** 최근 알림 5건 */
        List<UserAlert> recentAlerts,

        /** 최근 뉴스 5건 */
        List<NewsItem> recentNews,

        /** 면책 고지 */
        String disclaimer
) {
    /**
     * 주요 지수 스냅샷.
     * 값이 null이면 해당 API 미설정 또는 장 외 시간입니다.
     */
    public record IndicesSnapshot(
            IndexEntry kospi,
            IndexEntry kosdaq,
            IndexEntry btc
    ) {}

    /**
     * 단일 지수 항목.
     *
     * @param value      현재 지수값 (코인은 원화 현재가)
     * @param changeRate 전일 대비 등락률 (%)
     */
    public record IndexEntry(Double value, Double changeRate) {}
}
