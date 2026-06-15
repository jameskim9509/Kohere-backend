package com.kohere.diagnosis.application.dto;

import com.kohere.diagnosis.domain.DiagnosisStatus;
import java.time.Instant;

/**
 * 진단 제출(생성) 결과 DTO. 생성된 진단 식별자·상태·제출 시각만 담는다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §1 (201 Created).
 */
public record DiagnosisCreatedResponse(
    Long diagnosisId, DiagnosisStatus status, Instant submittedAt) {}
