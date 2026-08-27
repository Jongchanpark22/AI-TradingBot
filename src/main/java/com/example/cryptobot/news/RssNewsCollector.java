package com.example.cryptobot.news;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 언론사 RSS 피드 수집기.
 * API 키 불필요. application.yml의 news.rss.urls 목록을 폴링합니다.
 * RSS 2.0 표준 포맷 지원 (item/title, item/link, item/description, item/pubDate).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RssNewsCollector implements NewsSource {

    /**
     * RFC 822 날짜 포맷 변형 목록.
     * 한국 언론사는 "dd"(두 자리) 또는 "d"(한 자리) 일자를 혼용하므로 두 패턴을 모두 시도합니다.
     */
    private static final List<DateTimeFormatter> PUBDATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z",       Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z",        Locale.ENGLISH)
    );

    /**
     * DateTimeFormatter의 'Z' 패턴은 "+0900" 형태는 파싱하지만
     * "KST", "GMT" 같은 타임존 약어는 파싱하지 못합니다.
     * 파싱 전 약어를 UTC 오프셋 문자열로 치환합니다.
     */
    private static final Map<String, String> TZ_ABBR = Map.ofEntries(
            Map.entry("KST", "+0900"),
            Map.entry("JST", "+0900"),
            Map.entry("EST", "-0500"),
            Map.entry("EDT", "-0400"),
            Map.entry("CST", "-0600"),
            Map.entry("CDT", "-0500"),
            Map.entry("MST", "-0700"),
            Map.entry("MDT", "-0600"),
            Map.entry("PST", "-0800"),
            Map.entry("PDT", "-0700"),
            Map.entry("UTC", "+0000")
    );

    private final NewsClassifier classifier;
    private final NewsSymbolMatcher symbolMatcher;

    /** RSS 피드 URL 목록 (쉼표 구분, application.yml에서 관리) */
    @Value("${news.rss.urls:}")
    private String rssFeedUrls;

    @Override
    public String sourceName() {
        return "RSS";
    }

    /**
     * 설정된 모든 RSS 피드에서 키워드 포함 기사를 수집합니다.
     * keyword가 제목 또는 요약에 포함된 기사만 반환합니다.
     *
     * @param keyword    종목명 또는 테마 키워드
     * @param maxResults 피드당 최대 수집 건수 (전체 기사 수는 feed수 × maxResults 이하)
     */
    @Override
    public List<NewsItem> collect(String keyword, int maxResults) {
        List<NewsItem> results = new ArrayList<>();

        if (rssFeedUrls == null || rssFeedUrls.isBlank()) {
            log.debug("RSS 피드 URL 미설정 (news.rss.urls) — 건너뜀");
            return results;
        }

        String[] urls = rssFeedUrls.split(",");
        for (String rawUrl : urls) {
            String feedUrl = rawUrl.trim();
            if (feedUrl.isEmpty()) continue;
            try {
                List<NewsItem> items = fetchFromFeed(feedUrl, keyword, maxResults);
                results.addAll(items);
                log.info("RSS OK   {} (키워드='{}', {}건 파싱)", feedUrl, keyword, items.size());
            } catch (Exception e) {
                log.warn("RSS FAIL {} cause={}", feedUrl, e.getMessage());
            }
        }

        return results;
    }

    /**
     * RSS 피드를 전체 수집합니다 (키워드 필터 없음). NewsService 전체 폴링용.
     * 피드별 성공/실패를 INFO/WARN으로 기록하며, 실패해도 다음 피드를 계속 수집합니다.
     *
     * @return (시도, 성공, 실패, 총 파싱 건수) 요약 집계
     */
    public FetchSummary fetchAll(int maxPerFeed) {
        int tried = 0, succeeded = 0, failed = 0;
        List<NewsItem> results = new ArrayList<>();

        if (rssFeedUrls == null || rssFeedUrls.isBlank()) {
            log.warn("RSS 피드 URL 미설정 (news.rss.urls) — 수집 건너뜀");
            return new FetchSummary(0, 0, 0, results);
        }

        for (String rawUrl : rssFeedUrls.split(",")) {
            String feedUrl = rawUrl.trim();
            if (feedUrl.isEmpty()) continue;
            tried++;
            try {
                List<NewsItem> items = fetchFromFeed(feedUrl, null, maxPerFeed);
                results.addAll(items);
                if (items.isEmpty()) {
                    log.info("RSS OK   {} (0건 — 빈 피드이거나 항목 없음)", feedUrl);
                } else {
                    log.info("RSS OK   {} ({}건 파싱)", feedUrl, items.size());
                }
                succeeded++;
            } catch (Exception e) {
                log.warn("RSS FAIL {} cause={}", feedUrl, e.getMessage());
                failed++;
            }
        }

        return new FetchSummary(tried, succeeded, failed, results);
    }

    /** fetchAll() 결과 요약 */
    public record FetchSummary(int tried, int succeeded, int failed, List<NewsItem> items) {}

    /**
     * 단일 RSS 피드에서 기사를 파싱합니다.
     */
    private List<NewsItem> fetchFromFeed(String feedUrl, String keyword, int maxResults) throws Exception {
        List<NewsItem> items = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE 방지
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        URL url = new URL(feedUrl);
        try (InputStream is = url.openStream()) {
            Document doc = builder.parse(is);
            NodeList itemNodes = doc.getElementsByTagName("item");

            for (int i = 0; i < Math.min(itemNodes.getLength(), maxResults); i++) {
                Element item = (Element) itemNodes.item(i);

                String title       = textOf(item, "title");
                String link        = textOf(item, "link");
                String description = textOf(item, "description");
                String pubDate     = textOf(item, "pubDate");

                if (link == null || link.isBlank()) continue;

                // 키워드 필터 (null이면 전체 수집)
                if (keyword != null && !containsKeyword(title, description, keyword)) continue;

                // HTML 태그 제거
                String cleanSummary = description != null
                        ? description.replaceAll("<[^>]+>", "").trim()
                        : null;

                String trimTitle = title != null ? title.trim() : "제목 없음";
                items.add(NewsItem.builder()
                        .source(sourceName())
                        .url(link.trim())
                        .title(trimTitle)
                        .summary(cleanSummary)
                        .publishedAt(parsePubDate(pubDate))
                        .themes(classifier.classify(trimTitle, cleanSummary))
                        .linkedSymbols(symbolMatcher.match(trimTitle, cleanSummary))
                        .build());
            }
        }

        return items;
    }

    private boolean containsKeyword(String title, String description, String keyword) {
        String lower = keyword.toLowerCase(Locale.KOREAN);
        return (title != null && title.toLowerCase(Locale.KOREAN).contains(lower))
                || (description != null && description.toLowerCase(Locale.KOREAN).contains(lower));
    }

    /** RSS item의 단일 태그 텍스트를 반환합니다. */
    private String textOf(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    /**
     * RFC 822 pubDate → LocalDateTime.
     * 한국 언론사는 "KST" 같은 타임존 약어를 사용하는 경우가 있어
     * 파싱 전 약어를 오프셋 문자열로 교체한 후 여러 패턴을 순차 시도합니다.
     * 모든 패턴이 실패하면 수집 시각으로 폴백합니다.
     */
    private LocalDateTime parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return LocalDateTime.now();

        // 타임존 약어를 UTC 오프셋으로 교체 (GMT+0900 형태는 건드리지 않음)
        String cleaned = pubDate.trim();
        for (Map.Entry<String, String> tz : TZ_ABBR.entrySet()) {
            // \b 단어 경계로 "GMT+0900"의 "GMT"를 건드리지 않음
            cleaned = cleaned.replaceAll("\\b" + tz.getKey() + "\\b", tz.getValue());
        }

        for (DateTimeFormatter fmt : PUBDATE_FORMATS) {
            try {
                return ZonedDateTime.parse(cleaned, fmt).toLocalDateTime();
            } catch (Exception ignored) {}
        }

        log.debug("pubDate 파싱 실패: '{}' — 수집 시각 폴백", pubDate);
        return LocalDateTime.now();
    }
}
