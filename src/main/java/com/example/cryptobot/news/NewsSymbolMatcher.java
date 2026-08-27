package com.example.cryptobot.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 종목명 사전(하드코딩) 기반 뉴스 종목 매처.
 * 제목+요약에서 종목명을 감지하여 종목코드(국내주식 6자리 / 코인 KRW-XXX)로 변환합니다.
 *
 * <p>사전 우선순위: 긴 이름이 짧은 이름보다 먼저 등록되어 부분 오매칭을 방지합니다.
 * 예: "삼성전자우"를 "삼성전자"보다 먼저 등록.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSymbolMatcher {

    private final ObjectMapper objectMapper;

    /**
     * 종목명(또는 별칭) → 종목코드 사전.
     * 코스피·코스닥 주요 종목 + 주요 암호화폐.
     * 향후 KRX 종목마스터 파일 로드 방식으로 교체 가능합니다.
     */
    private static final Map<String, String> DICT = new LinkedHashMap<>();

    static {
        // ── 코스피 대형주 (시총 순) ────────────────────────────────────────────
        DICT.put("삼성전자우", "005935");
        DICT.put("삼성전자", "005930");
        DICT.put("삼전", "005930");
        DICT.put("SK하이닉스", "000660");
        DICT.put("하이닉스", "000660");
        DICT.put("현대자동차", "005380");
        DICT.put("현대차", "005380");
        DICT.put("기아자동차", "000270");
        DICT.put("기아", "000270");
        DICT.put("삼성바이오로직스", "207940");
        DICT.put("삼바", "207940");
        DICT.put("셀트리온", "068270");
        DICT.put("LG에너지솔루션", "373220");
        DICT.put("LG엔솔", "373220");
        DICT.put("POSCO홀딩스", "005490");
        DICT.put("포스코홀딩스", "005490");
        DICT.put("포스코", "005490");
        DICT.put("삼성SDI", "006400");
        DICT.put("LG화학", "051910");
        DICT.put("LG전자", "066570");
        DICT.put("현대모비스", "012330");
        DICT.put("삼성물산", "028260");
        DICT.put("KB금융", "105560");
        DICT.put("신한지주", "055550");
        DICT.put("하나금융지주", "086790");
        DICT.put("우리금융지주", "316140");
        DICT.put("SK텔레콤", "017670");
        DICT.put("SK이노베이션", "096770");
        DICT.put("롯데케미칼", "011170");
        DICT.put("한국전력", "015760");
        DICT.put("한전", "015760");
        DICT.put("두산에너빌리티", "034020");
        DICT.put("한화에어로스페이스", "012450");
        DICT.put("HD현대중공업", "329180");
        DICT.put("현대중공업", "329180");
        DICT.put("한화오션", "042660");
        DICT.put("카카오뱅크", "323410");
        DICT.put("카카오", "035720");
        DICT.put("NAVER", "035420");
        DICT.put("네이버", "035420");
        DICT.put("삼성생명", "032830");
        DICT.put("KT&G", "033780");
        DICT.put("고려아연", "010130");

        // ── 코스닥 대형주 ─────────────────────────────────────────────────────
        DICT.put("에코프로비엠", "247540");
        DICT.put("에코프로", "086520");
        DICT.put("HLB", "028300");
        DICT.put("리노공업", "058470");
        DICT.put("레인보우로보틱스", "277810");
        DICT.put("알테오젠", "196170");

        // ── 주요 암호화폐 ─────────────────────────────────────────────────────
        DICT.put("비트코인", "KRW-BTC");
        DICT.put("Bitcoin", "KRW-BTC");
        DICT.put("이더리움", "KRW-ETH");
        DICT.put("Ethereum", "KRW-ETH");
        DICT.put("리플", "KRW-XRP");
        DICT.put("솔라나", "KRW-SOL");
        DICT.put("도지코인", "KRW-DOGE");
        DICT.put("도지", "KRW-DOGE");
        DICT.put("에이다", "KRW-ADA");
        DICT.put("아발란체", "KRW-AVAX");
        DICT.put("폴리곤", "KRW-MATIC");
        DICT.put("체인링크", "KRW-LINK");
    }

    /**
     * 제목과 요약에서 종목을 감지하여 종목코드 JSON 배열 문자열을 반환합니다.
     * 매칭 없으면 "[]" 반환.
     *
     * @param title   기사 제목
     * @param summary 기사 요약
     * @return 종목코드 JSON 배열 (예: ["005930","KRW-BTC"])
     */
    public String match(String title, String summary) {
        String text = (title != null ? title : "") + " " + (summary != null ? summary : "");
        // LinkedHashSet: 삽입 순서 유지 + 중복 제거
        Set<String> codes = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : DICT.entrySet()) {
            if (text.contains(entry.getKey())) {
                codes.add(entry.getValue());
            }
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(codes));
        } catch (Exception e) {
            log.debug("종목 JSON 직렬화 실패", e);
            return "[]";
        }
    }
}
