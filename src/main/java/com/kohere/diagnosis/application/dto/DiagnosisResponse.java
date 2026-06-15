package com.kohere.diagnosis.application.dto;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.DiagnosisStatus;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import java.time.Instant;
import java.util.List;

/**
 * 진단 단건/이력 항목 응답 DTO. 진단 입력 전체를 다시 보여준다(이력 목록·단건 상세 공용). 표현 계층은 이를 공통 래퍼로 감싼다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §2·§4.
 */
public record DiagnosisResponse(
    Long diagnosisId,
    Region region,
    List<Purpose> purposes,
    List<DiagnosisCondition> conditions,
    int monthlyBudgetMax,
    ArcStatus arcStatus,
    DiagnosisStatus status,
    Instant submittedAt) {}
