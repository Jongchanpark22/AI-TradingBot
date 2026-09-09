package com.example.cryptobot.home;

import com.example.cryptobot.home.dto.HomeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 브리핑 API.
 *
 * <p>앱 홈 화면에 필요한 데이터(지수·보유종목·알림·뉴스)를 한 번에 반환합니다.</p>
 * <p>⚠️ 면책: 제공 정보는 참고용이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 홈 브리핑 조회.
     *
     * <ul>
     *   <li>코스피·코스닥: 토스증권 Open API (TOSS_CLIENT_ID/SECRET 설정 시)</li>
     *   <li>BTC: 업비트 공개 API</li>
     *   <li>보유종목·알림·뉴스: 내부 DB</li>
     * </ul>
     */
    @GetMapping
    public HomeResponse getHome(@AuthenticationPrincipal Long userId) {
        return homeService.buildHome(userId);
    }
}
