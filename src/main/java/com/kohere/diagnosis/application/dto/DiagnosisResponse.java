package com.kohere.diagnosis.application.dto;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.DiagnosisStatus;
import com.kohere.diagnosis.domain.District;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import com.kohere.diagnosis.domain.UniversityGroup;
import java.time.Instant;
import java.util.List;

/**
 * 진단 단건/이력 항목 응답 DTO. 진단 입력 전체를 다시 보여준다(이력 목록·단건 상세 공용). 표현 계층은 이를 공통 래퍼로 감싼다.
 *
 * <p>입국 목적에 따라 {@code university}(STUDY) 또는 {@code district}(NON_STUDY) 중 하나만 채워지고 반대 필드는 null이다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §2·§4.
 */
public record DiagnosisResponse(
    Long diagnosisId,
    Region region,
    Purpose purpose,
    UniversityGroup university,
    District district,
    List<DiagnosisCondition> conditions,
    int monthlyRentMin,
    int monthlyRentMax,
    ArcStatus arcStatus,
    DiagnosisStatus status,
    Instant submittedAt) {}
