package com.example.cryptobot.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 제목·요약 키워드 기반 뉴스 테마 분류기.
 * LLM 없이 NewsTheme enum의 키워드 목록과 대소문자 무관 부분 매칭으로 동작합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsClassifier {

    private final ObjectMapper objectMapper;

    /**
     * 제목과 요약을 스캔하여 매칭된 소분류 테마 코드를 JSON 배열 문자열로 반환합니다.
     * 매칭 없으면 "[]" 반환(억지 분류 없음).
     *
     * @param title   기사 제목
     * @param summary 기사 요약
     * @return 소분류 테마 코드 JSON 배열 (예: ["RATE","GLOBAL_ECON"])
     */
    public String classify(String title, String summary) {
        String text = buildText(title, summary);
        List<String> matched = Arrays.stream(NewsTheme.values())
                .filter(theme -> theme.getKeywords().stream()
                        .anyMatch(kw -> text.contains(kw.toLowerCase(Locale.KOREAN))))
                .map(Enum::name)
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(matched);
        } catch (Exception e) {
            log.debug("테마 JSON 직렬화 실패", e);
            return "[]";
        }
    }

    /** 제목+요약을 소문자로 합쳐 반환합니다. */
    private String buildText(String title, String summary) {
        return ((title != null ? title : "") + " " + (summary != null ? summary : ""))
                .toLowerCase(Locale.KOREAN);
    }
}
