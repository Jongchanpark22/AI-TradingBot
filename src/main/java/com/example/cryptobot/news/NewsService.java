package com.example.cryptobot.news;

import com.example.cryptobot.holding.UserHoldingRepository;
import com.example.cryptobot.holding.WatchlistRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 뉴스·공시 수집 서비스.
 *
 * <ul>
 *   <li>DART 공시: 매일 오전 8시 수집</li>
 *   <li>RSS 전체 폴링: 30분 주기 (키 불필요)</li>
 *   <li>종목별 뉴스: 30분 주기 (등록된 NewsSource 활용)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final DartApiClient dartApiClient;
    private final DartDisclosureRepository dartRepository;
    private final NewsItemRepository newsItemRepository;
    private final UserHoldingRepository holdingRepository;
    private final WatchlistRepository watchlistRepository;
    private final List<NewsSource> newsSources;
    private final RssNewsCollector rssNewsCollector;

    // ─── DART 공시 수집 ──────────────────────────────────────────────────────

    /**
     * 매일 오전 8시 — 전일 DART 공시 수집 및 보유/관심 종목 매칭.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void collectDartDisclosures() {
        log.info("DART 공시 수집 시작");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today     = LocalDate.now();

        List<DartApiClient.DartRawItem> rawItems =
                dartApiClient.fetchDisclosures(yesterday, today);

        // 보유/관심 종목 기업명 세트 (매칭용)
        Set<String> watchedSymbols = collectWatchedSymbols();

        int saved = 0;
        for (DartApiClient.DartRawItem raw : rawItems) {
            if (dartRepository.existsByRceptNo(raw.rceptNo())) continue;

            // 보유/관심 종목 심볼과 기업명 단순 매칭
            String linkedSymbol = matchSymbol(raw.corpName(), watchedSymbols);

            DartDisclosure disclosure = DartDisclosure.builder()
                    .rceptNo(raw.rceptNo())
                    .corpCode(raw.corpCode())
                    .corpName(raw.corpName())
                    .title(raw.reportNm())
                    .disclosureDate(parseDate(raw.rceptDt()))
                    .url(raw.url())
                    .linkedSymbol(linkedSymbol)
                    .reportType(raw.reportNm())
                    .build();

            dartRepository.save(disclosure);
            saved++;
        }

        log.info("DART 공시 수집 완료: {}건 저장 (전체 {}건)", saved, rawItems.size());
    }

    // ─── 뉴스 수집 ──────────────────────────────────────────────────────────

    /**
     * 30분 주기 + 기동 직후 1회 즉시 실행 — RSS 전체 피드 폴링 (키 불필요, 빠른 갱신).
     * 모든 설정된 RSS 피드를 수집하여 중복 제거 후 저장합니다.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 30 * 60 * 1000L)
    @Transactional
    public void collectRssAll() {
        RssNewsCollector.FetchSummary summary = rssNewsCollector.fetchAll(50);
        int saved = saveNewsItems(summary.items());
        log.info("RSS 전체 폴링 완료: 시도={}개, 성공={}개, 실패={}개, 신규저장={}건",
                summary.tried(), summary.succeeded(), summary.failed(), saved);
    }

    /**
     * RSS 수집 결과 요약 반환 (수동 트리거 용도).
     * 스케줄러와 별개로 즉시 수집 후 결과를 응답으로 반환합니다.
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
        Set<String> watchedSymbols = collectWatchedSymbols();
        int saved = 0;
        for (DartApiClient.DartRawItem raw : rawItems) {
            if (dartRepository.existsByRceptNo(raw.rceptNo())) continue;
            String linkedSymbol = matchSymbol(raw.corpName(), watchedSymbols);
            DartDisclosure disclosure = DartDisclosure.builder()
                    .rceptNo(raw.rceptNo()).corpCode(raw.corpCode()).corpName(raw.corpName())
                    .title(raw.reportNm()).disclosureDate(parseDate(raw.rceptDt()))
                    .url(raw.url()).linkedSymbol(linkedSymbol).reportType(raw.reportNm())
                    .build();
            dartRepository.save(disclosure);
            saved++;
        }
        log.info("[수동트리거] DART 수집 완료: {}건 저장 (전체 {}건)", saved, rawItems.size());
        return saved;
    }

    /** 수동 수집 결과 요약 DTO */
    public record CollectResult(int feedsTried, int feedsSucceeded, int feedsFailed, int newsSaved) {}

    /**
     * 30분 주기 — 보유/관심 종목 키워드 뉴스 수집.
     * 등록된 NewsSource(RSS 등)를 활용합니다.
     */
    @Scheduled(cron = "0 15 */1 * * *")
    @Transactional
    public void collectNewsBySymbol() {
        Set<String> symbols = collectWatchedSymbols();
        if (symbols.isEmpty()) {
            log.debug("보유/관심 종목 없음 — 종목별 뉴스 수집 건너뜀");
            return;
        }

        log.info("종목별 뉴스 수집 시작: {} 종목", symbols.size());
        int total = 0;

        for (String symbol : symbols) {
            for (NewsSource source : newsSources) {
                List<NewsItem> items = source.collect(symbol, 20);
                total += saveNewsItems(items);
            }
        }

        log.info("종목별 뉴스 수집 완료: {}건 신규 저장", total);
    }

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
     *
     * @param cursor 이전 응답의 nextCursor (null이면 첫 페이지)
     * @param size   페이지 크기 (1~50)
     * @param sort   "latest" 또는 "views"
     */
    @Transactional
    public NewsFeedResponse getNewsFeed(String cursor, int size, String sort) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageable = PageRequest.of(0, pageSize + 1); // +1로 다음 페이지 존재 여부 확인

        List<NewsItem> rows;

        if ("views".equals(sort)) {
            long[] parsed = parseCursorViews(cursor);
            rows = newsItemRepository.findByViewsCursor(
                    cursor == null ? null : parsed[0],
                    cursor == null ? null : parsed[1],
                    pageable);
        } else {
            Object[] parsed = parseCursorLatest(cursor);
            rows = newsItemRepository.findByLatestCursor(
                    (LocalDateTime) parsed[0],
                    (Long) parsed[1],
                    pageable);
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

    /** 최신순 커서: "yyyyMMddHHmmss_id" */
    private Object[] parseCursorLatest(String cursor) {
        if (cursor == null) return new Object[]{null, null};
        try {
            String[] parts = cursor.split("_");
            return new Object[]{LocalDateTime.parse(parts[0], CURSOR_FMT), Long.parseLong(parts[1])};
        } catch (Exception e) {
            return new Object[]{null, null};
        }
    }

    /** 조회수순 커서: "views_id" */
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

    /** 중복 URL 제외 후 저장, 저장 건수 반환. */
    private int saveNewsItems(List<NewsItem> items) {
        List<NewsItem> toSave = items.stream()
                .filter(item -> !newsItemRepository.existsByUrl(item.getUrl()))
                .toList();
        if (!toSave.isEmpty()) {
            newsItemRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    /**
     * 보유 종목 + 관심 종목 심볼 코드를 수집합니다.
     */
    private Set<String> collectWatchedSymbols() {
        Set<String> symbols = holdingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(h -> h.getSymbol())
                .collect(Collectors.toSet());
        watchlistRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(w -> w.getSymbol())
                .forEach(symbols::add);
        return symbols;
    }

    /**
     * 기업명과 심볼 코드 단순 매칭 (포함 여부).
     * 예: "삼성전자" 공시 → 관심 종목에 "삼성전자" 포함 시 해당 코드 반환.
     */
    private String matchSymbol(String corpName, Set<String> symbols) {
        for (String symbol : symbols) {
            if (corpName != null && corpName.contains(symbol)) return symbol;
        }
        return null;
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
