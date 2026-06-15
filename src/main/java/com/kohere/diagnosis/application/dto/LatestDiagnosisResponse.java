package com.kohere.diagnosis.application.dto;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import java.time.Instant;
import java.util.List;

/**
 * 최근 진단 단건 응답 DTO. 홈 화면의 "진단 시작 / 재진단" 분기를 위해 {@code completed}로 진단 이력 유무를 알린다. 이력이 없으면 {@code
 * completed=false}만 내려가고 진단 요약 필드는 모두 null이다(404 아님).
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §3.
 */
public record LatestDiagnosisResponse(
    boolean completed,
    Long diagnosisId,
    Region region,
    List<Purpose> purposes,
    List<DiagnosisCondition> conditions,
    Integer monthlyBudgetMax,
    ArcStatus arcStatus,
    Instant submittedAt) {}
