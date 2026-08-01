package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.CompanyFinancials;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * AI 기업 리포트 응답 DTO.
 *
 * <p>⚠️ 면책 고지: 본 리포트는 DART 공시 재무 데이터 기반 참고 정보이며,
 * 투자 권유가 아닙니다. 목표주가·매수/매도 의견은 제공하지 않습니다.</p>
 */
@Getter
@Builder
public class AiReportResponse {

    /** 기업 코드 */
    private String corpCode;

    /** 기업명 */
    private String corpName;

    /** 사업연도 */
    private int businessYear;

    /** Java에서 계산한 재무 지표 요약 */
    private CompanyFinancials financials;

    /** LLM이 생성한 한국어 서술 (숫자는 financials 기반, LLM이 직접 생성한 숫자 없음) */
    private String narrative;

    /** 리포트 생성 시각 */
    private LocalDateTime generatedAt;

    /** 면책 고지 */
    @Builder.Default
    private String disclaimer =
            "본 리포트는 DART 공시 재무 데이터를 기반으로 한 참고 정보입니다. "
            + "목표주가 및 매수/매도 의견은 제공하지 않으며, 투자 권유가 아닙니다. "
            + "투자는 본인 판단과 책임 하에 진행하시기 바랍니다.";
}
