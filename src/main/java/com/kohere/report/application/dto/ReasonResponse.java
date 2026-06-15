package com.kohere.report.application.dto;

/**
 * 신고 사유 메타 항목. 클라이언트는 {@code code}로 다국어 표기를 매핑하고, {@code label}은 서버 기본 한국어 문구다.
 * docs/api/specs/07-reports.md (GET /reasons).
 */
public record ReasonResponse(String code, String label) {}
