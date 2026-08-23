package com.example.cryptobot.news;

import com.example.cryptobot.news.dto.NewsFeedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 뉴스·공시 수집 서비스.
 *
 * <ul>
 *   <li>RSS 전체 폴링: 기동 직후 1회 + 30분 주기 — 키워드 필터 없이 전체 기사 저장</li>
 *   <li>DART 공시: 매일 오전 8시 수집 — 보유종목 여부와 무관하게 전체 저장</li>
 *   <li>종목·테마 매칭은 저장 게이트가 아닌 부가 태그(linkedSymbols)로만 사용</li>
 *   <li>필터링은 조회 API(getNewsFeed)에서 처리</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final DartApiClient dartApiClient;
    private final DartDisclosureRepository dartRepository;
    private final NewsItemRepository newsItemRepository;
    private final RssNewsCollector rssNewsCollector;

    // ─── DART 공시 수집 ──────────────────────────────────────────────────────

    /**
     * 매일 오전 8시 — 전일 DART 공시 전체 수집.
     * 보유종목 여부와 무관하게 모두 저장하며, 종목 매칭은 linkedSymbol 태그로만 사용합니다.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void collectDartDisclosures() {
        log.info("DART 공시 수집 시작");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today     = LocalDate.now();

        List<DartApiClient.DartRawItem> rawItems =
                dartApiClient.fetchDisclosures(yesterday, today);

        int saved = 0;
        for (DartApiClient.DartRawItem raw : rawItems) {
            if (dartRepository.existsByRceptNo(raw.rceptNo())) continue;

            dartRepository.save(DartDisclosure.builder()
                    .rceptNo(raw.rceptNo())
                    .corpCode(raw.corpCode())
                    .corpName(raw.corpName())
                    .title(raw.reportNm())
                    .disclosureDate(parseDate(raw.rceptDt()))
                    .url(raw.url())
                    .linkedSymbol(null)   // 태그는 향후 별도 매칭 단계에서 업데이트
                    .reportType(raw.reportNm())
                    .build());
            saved++;
        }

        log.info("DART 공시 수집 완료: {}건 저장 (전체 {}건)", saved, rawItems.size());
    }

    // ─── RSS 뉴스 수집 ──────────────────────────────────────────────────────

    /**
     * 기동 직후 1회 즉시 실행 + 30분 주기 — RSS 전체 피드 폴링.
     * 키워드 필터 없이 모든 기사를 저장합니다. dedup은 URL 기준.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 30 * 60 * 1000L)
    @Transactional
    public void collectRssAll() {
        RssNewsCollector.FetchSummary summary = rssNewsCollector.fetchAll(50);
        int saved = saveNewsItems(summary.items());
        log.info("RSS 전체 폴링 완료: 시도={}개, 성공={}개, 실패={}개, 신규저장={}건",
                summary.tried(), summary.succeeded(), summary.failed(), saved);
    }

    // ─── 수동 트리거 ─────────────────────────────────────────────────────────

    /**
     * RSS 즉시 수집 (수동 트리거 용도).
     */
    @Transactional
    public CollectResult triggerRssCollect() {
        RssNewsCollector.FetchSummary summary = rssNewsCollector.fetchAll(50);
        int saved = saveNewsItems(summary.items());
        log.info("[수동트리거] RSS 수집 완료: 시도={}, 성공={}, 실패={}, 저장={}",
                summary.tried(), summary.succeeded(), summary.failed(), saved);
        return new CollectResult(summary.tried(), summary.succeeded(), summary.failed(), saved);
    }

    /**
     * DART 공시 즉시 수집 (수동 트리거 용도).
     */
    @Transactional
    public int triggerDartCollect() {
        log.info("[수동트리거] DART 공시 수집 시작");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today     = LocalDate.now();
        List<DartApiClient.DartRawItem> rawItems = dartApiClient.fetchDisclosures(yesterday, today);
        int saved = 0;
        for (DartApiClient.DartRawItem raw : rawItems) {
            if (dartRepository.existsByRceptNo(raw.rceptNo())) continue;
            dartRepository.save(DartDisclosure.builder()
                    .rceptNo(raw.rceptNo()).corpCode(raw.corpCode()).corpName(raw.corpName())
                    .title(raw.reportNm()).disclosureDate(parseDate(raw.rceptDt()))
                    .url(raw.url()).linkedSymbol(null).reportType(raw.reportNm())
                    .build());
            saved++;
        }
        log.info("[수동트리거] DART 수집 완료: {}건 저장 (전체 {}건)", saved, rawItems.size());
        return saved;
    }

    /** 수동 수집 결과 요약 DTO */
    public record CollectResult(int feedsTried, int feedsSucceeded, int feedsFailed, int newsSaved) {}

    // ─── 조회 API ─────────────────────────────────────────────────────────────

    /**
     * 최근 N일 DART 공시 목록 반환.
     */
    public List<DartDisclosure> getRecentDisclosures(int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        return dartRepository.findByDisclosureDateGreaterThanEqualOrderByDisclosureDateDesc(from);
    }

    /**
     * 특정 심볼 관련 DART 공시 반환.
     */
    public List<DartDisclosure> getDisclosuresBySymbol(String symbol) {
        return dartRepository.findByLinkedSymbolOrderByDisclosureDateDesc(symbol);
    }

    /**
     * 최근 N시간 뉴스 목록 반환 (기존 호환용).
     */
    public List<NewsItem> getRecentNews(int hours) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return newsItemRepository.findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(from);
    }

    /**
     * 커서 기반 뉴스 피드 페이지네이션.
     * symbols, themes 파라미터로 linkedSymbols/themes 컬럼 LIKE 필터를 적용합니다.
     *
     * @param cursor  이전 응답의 nextCursor (null이면 첫 페이지)
     * @param size    페이지 크기 (1~50)
     * @param sort    "latest" 또는 "views"
     * @param symbols 종목 코드 필터 (예: "KRW-BTC") — null이면 전체
     * @param themes  테마 키워드 필터 (예: "금리") — null이면 전체
     */
    @Transactional
    public NewsFeedResponse getNewsFeed(String cursor, int size, String sort,
                                        String symbols, String themes) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageable = PageRequest.of(0, pageSize + 1);

        // LIKE 패턴: null이면 필터 없음, 값이 있으면 JSON 배열 문자열 내 포함 여부 검사
        String symbolLike = symbols != null && !symbols.isBlank() ? "%" + symbols.trim() + "%" : null;
        String themeLike  = themes  != null && !themes.isBlank()  ? "%" + themes.trim()  + "%" : null;

        List<NewsItem> rows;

        if ("views".equals(sort)) {
            long[] parsed = parseCursorViews(cursor);
            rows = newsItemRepository.findByViewsCursorFiltered(
                    cursor == null ? null : parsed[0],
                    cursor == null ? null : parsed[1],
                    symbolLike, themeLike, pageable);
        } else {
            Object[] parsed = parseCursorLatest(cursor);
            rows = newsItemRepository.findByLatestCursorFiltered(
                    (LocalDateTime) parsed[0],
                    (Long) parsed[1],
                    symbolLike, themeLike, pageable);
        }

        boolean hasNext = rows.size() > pageSize;
        List<NewsItem> items = hasNext ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasNext ? buildCursor(items.get(items.size() - 1), sort) : null;

        return NewsFeedResponse.builder()
                .items(items)
                .nextCursor(nextCursor)
                .size(items.size())
                .build();
    }

    /**
     * 뉴스 단건 조회 + 조회수 증가.
     */
    @Transactional
    public java.util.Optional<NewsItem> getNewsItem(Long id) {
        return newsItemRepository.findById(id).map(item -> {
            newsItemRepository.incrementViewCount(id);
            return item;
        });
    }

    // ─── 커서 파싱·빌드 ──────────────────────────────────────────────────────

    private static final DateTimeFormatter CURSOR_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private Object[] parseCursorLatest(String cursor) {
        if (cursor == null) return new Object[]{null, null};
        try {
            String[] parts = cursor.split("_");
            return new Object[]{LocalDateTime.parse(parts[0], CURSOR_FMT), Long.parseLong(parts[1])};
        } catch (Exception e) {
            return new Object[]{null, null};
        }
    }

    private long[] parseCursorViews(String cursor) {
        if (cursor == null) return new long[]{Long.MAX_VALUE, Long.MAX_VALUE};
        try {
            String[] parts = cursor.split("_");
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (Exception e) {
            return new long[]{Long.MAX_VALUE, Long.MAX_VALUE};
        }
    }

    private String buildCursor(NewsItem last, String sort) {
        if ("views".equals(sort)) {
            return last.getViewCount() + "_" + last.getId();
        }
        return last.getPublishedAt().format(CURSOR_FMT) + "_" + last.getId();
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────────

    /** URL 중복 제외 후 저장, 저장 건수 반환. */
    private int saveNewsItems(List<NewsItem> items) {
        List<NewsItem> toSave = items.stream()
                .filter(item -> !newsItemRepository.existsByUrl(item.getUrl()))
                .toList();
        if (!toSave.isEmpty()) {
            newsItemRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    /** "20240115" → LocalDate */
    private LocalDate parseDate(String str) {
        if (str == null || str.length() < 8) return LocalDate.now();
        try {
            return LocalDate.parse(str, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}
