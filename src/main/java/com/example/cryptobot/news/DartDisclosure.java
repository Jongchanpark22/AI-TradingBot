package com.example.cryptobot.news;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * DART Open API에서 수집한 기업 공시 정보.
 * 공시 날짜, 제목, 기업명, 연관 심볼을 저장합니다.
 */
@Entity
@Table(name = "dart_disclosure", indexes = {
        @Index(name = "idx_dart_corp",   columnList = "corp_code"),
        @Index(name = "idx_dart_date",   columnList = "disclosure_date"),
        @Index(name = "idx_dart_symbol", columnList = "linked_symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DartDisclosure extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DART 고유 공시 번호 (중복 수집 방지) */
    @Column(nullable = false, unique = true, length = 14)
    private String rceptNo;

    /** DART 기업 코드 */
    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    /** 기업명 */
    @Column(nullable = false, length = 100)
    private String corpName;

    /** 공시 제목 */
    @Column(nullable = false, length = 500)
    private String title;

    /** 공시 날짜 */
    @Column(name = "disclosure_date", nullable = false)
    private LocalDate disclosureDate;

    /** DART 공시 URL */
    @Column(length = 300)
    private String url;

    /** 매칭된 보유/관심 종목 코드 (예: KRW-BTC, 주식은 종목코드) */
    @Column(name = "linked_symbol", length = 20)
    private String linkedSymbol;

    /** 공시 종류 (분기보고서, 사업보고서 등) */
    @Column(length = 100)
    private String reportType;
}
