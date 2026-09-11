package com.example.cryptobot.chart;

import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.repository.UserRepository;
import com.example.cryptobot.common.apiPayload.ErrorCode;
import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 차트 주석·커스텀 지표 설정 API.
 */
@RestController
@RequestMapping("/chart")
@RequiredArgsConstructor
public class ChartAnnotationController {

    private final UserChartAnnotationRepository annotationRepository;
    private final UserIndicatorSettingRepository indicatorRepository;
    private final UserRepository userRepository;

    // ─── 차트 주석 ────────────────────────────────────────────────────────────

    /**
     * 심볼별 차트 주석 목록 조회.
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     */
    @GetMapping("/{symbol}/annotations")
    public List<UserChartAnnotation> getAnnotations(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol) {
        return annotationRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, symbol);
    }

    /**
     * 차트 주석 추가.
     * body 예: {"symbol":"KRW-BTC","type":"LINE","pointsJson":"[...]","note":"지지선"}
     */
    @PostMapping("/{symbol}/annotations")
    @Transactional
    public ResponseEntity<UserChartAnnotation> addAnnotation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol,
            @RequestBody UserChartAnnotation request) {
        request.setUserId(userId);
        request.setSymbol(symbol);
        return ResponseEntity.ok(annotationRepository.save(request));
    }

    /**
     * 차트 주석 삭제.
     */
    @DeleteMapping("/{symbol}/annotations/{id}")
    @Transactional
    public ResponseEntity<Void> deleteAnnotation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol,
            @PathVariable Long id) {
        annotationRepository.findByIdAndUserId(id, userId)
                .ifPresentOrElse(
                        annotationRepository::delete,
                        () -> { throw new BusinessException(ErrorCode._NOT_FOUND, "주석을 찾을 수 없습니다: " + id); }
                );
        return ResponseEntity.noContent().build();
    }

    // ─── 커스텀 지표 설정 ──────────────────────────────────────────────────────

    /**
     * 심볼별 커스텀 지표 설정 목록 조회.
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     */
    @GetMapping("/{symbol}/indicators/settings")
    public List<UserIndicatorSetting> getIndicatorSettings(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol) {
        return indicatorRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, symbol);
    }

    /**
     * 커스텀 지표 설정 추가.
     *
     * <p>FREE 티어: 전체 지표 1개까지 허용. 초과 시 403(PREMIUM_REQUIRED).
     * PREMIUM 티어: 무제한.</p>
     *
     * body 예: {"indicatorType":"RSI","paramsJson":"{\"period\":14}","enabled":true}
     */
    @PostMapping("/{symbol}/indicators/settings")
    @Transactional
    public ResponseEntity<UserIndicatorSetting> addIndicatorSetting(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol,
            @RequestBody UserIndicatorSetting request) {

        // FREE 티어 지표 수 게이트
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode._NOT_FOUND));
        if (user.getTier() == User.Tier.FREE) {
            long count = indicatorRepository.countByUserId(userId);
            if (count >= 1) {
                throw new BusinessException(ErrorCode.PREMIUM_REQUIRED);
            }
        }

        request.setUserId(userId);
        request.setSymbol(symbol);
        return ResponseEntity.ok(indicatorRepository.save(request));
    }

    /**
     * 커스텀 지표 설정 삭제.
     */
    @DeleteMapping("/{symbol}/indicators/settings/{id}")
    @Transactional
    public ResponseEntity<Void> deleteIndicatorSetting(
            @AuthenticationPrincipal Long userId,
            @PathVariable String symbol,
            @PathVariable Long id) {
        indicatorRepository.findByIdAndUserId(id, userId)
                .ifPresentOrElse(
                        indicatorRepository::delete,
                        () -> { throw new BusinessException(ErrorCode._NOT_FOUND, "지표 설정을 찾을 수 없습니다: " + id); }
                );
        return ResponseEntity.noContent().build();
    }
}
