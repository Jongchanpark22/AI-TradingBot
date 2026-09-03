package com.example.cryptobot.report.dto;

/**
 * 리포트 생성 요청 즉시 응답 DTO.
 * 클라이언트는 reportId로 GET /report/saved/{id}를 폴링하여 완료 여부를 확인합니다.
 */
public record ReportSubmitResponse(Long reportId, String status) {}
