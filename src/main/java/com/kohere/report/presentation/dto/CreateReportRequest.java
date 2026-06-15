package com.kohere.report.presentation.dto;

import com.kohere.report.domain.ReportReason;
import com.kohere.report.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 콘텐츠 신고 접수 요청 바디. 입력 형식 검증은 표현 계층에서 수행한다(docs/convention/code-style.md §3-3).
 * docs/api/specs/07-reports.md (POST /reports Request Body).
 */
public record CreateReportRequest(
    @NotNull ReportTargetType targetType,
    @NotNull Long targetId,
    @NotNull ReportReason reason,
    @Size(max = 500) String detail) {}
