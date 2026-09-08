package com.example.cryptobot.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 온보딩 완료 요청 DTO.
 * 관심 종목·테마를 한 번에 등록하고 onboardedAt을 기록합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class OnboardingRequest {

    /** 관심 종목 코드 목록 (예: KRW-BTC, 005930) */
    private List<String> symbols;

    /** 관심 테마 목록 (예: 금리, 반도체) */
    private List<String> themes;
}
