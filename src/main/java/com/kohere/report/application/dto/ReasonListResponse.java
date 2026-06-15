package com.kohere.report.application.dto;

import java.util.List;

/**
 * 신고 사유 메타 목록 응답. 사유는 고정·소규모 집합이라 페이지네이션 없이 전체를 한 번에 반환한다. docs/api/specs/07-reports.md (GET
 * /reasons).
 */
public record ReasonListResponse(List<ReasonResponse> reasons) {}
