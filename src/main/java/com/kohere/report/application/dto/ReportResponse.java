package com.kohere.report.application.dto;

import com.kohere.report.domain.ReportReason;
import com.kohere.report.domain.ReportStatus;
import com.kohere.report.domain.ReportTargetType;
import java.time.Instant;

/**
 * 신고 접수 결과 응답 DTO. 신고자 식별 정보(reporterId)와 detail 원문은 프라이버시 보호를 위해 노출하지 않는다(error-response-guide
 * §6). docs/api/specs/07-reports.md (POST /reports 201 응답).
 */
public record ReportResponse(
    Long reportId,
    ReportTargetType targetType,
    Long targetId,
    ReportReason reason,
    ReportStatus status,
    Instant createdAt) {}
