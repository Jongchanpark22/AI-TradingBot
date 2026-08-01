package com.example.cryptobot.news;

import com.example.cryptobot.holding.UserHoldingRepository;
import com.example.cryptobot.holding.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>DART 공시: 매일 오전 8시 수집, 보유/관심 종목명 매칭</li>
 *   <li>빅카인즈·네이버 뉴스: 매일 오전 8시 30분 수집, 보유/관심 종목 키워드 검색</li>
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
     * 매일 오전 8시 30분 — 보유/관심 종목 키워드 뉴스 수집.
     * 빅카인즈, 네이버 뉴스 순으로 수집 (API 키 미설정 시 건너뜀).
     */
    @Scheduled(cron = "0 30 8 * * *")
    @Transactional
    public void collectNews() {
        Set<String> symbols = collectWatchedSymbols();
        if (symbols.isEmpty()) {
            log.debug("보유/관심 종목 없음 — 뉴스 수집 건너뜀");
            return;
        }

        log.info("뉴스 수집 시작: {} 종목", symbols.size());
        int total = 0;

        for (String symbol : symbols) {
            for (NewsSource source : newsSources) {
                List<NewsItem> items = source.collect(symbol, 20);
                List<NewsItem> toSave = new ArrayList<>();

                for (NewsItem item : items) {
                    if (!newsItemRepository.existsByUrl(item.getUrl())) {
                        toSave.add(item);
                    }
                }

                if (!toSave.isEmpty()) {
                    newsItemRepository.saveAll(toSave);
                    total += toSave.size();
                }
            }
        }

        log.info("뉴스 수집 완료: {}건 저장", total);
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
     * 최근 N시간 뉴스 목록 반환.
     */
    public List<NewsItem> getRecentNews(int hours) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return newsItemRepository.findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(from);
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────────

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
