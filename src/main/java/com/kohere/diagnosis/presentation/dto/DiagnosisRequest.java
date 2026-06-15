package com.kohere.diagnosis.presentation.dto;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 진단 제출 요청 바디. 5단계 진단 입력을 담는다. 형식 검증은 표현 계층(Bean Validation)에서 처리하고, 위반은 전역 핸들러가 {@code
 * INVALID_INPUT}(400) + {@code errors[]}로 변환한다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §1.
 *
 * @param region ① 지역(단일, 필수)
 * @param purposes ② 입국 목적(다중, 최소 1개. 중복은 도메인에서 제거)
 * @param conditions ③ 주거 환경 조건(다중, 최대 3개. 중복은 도메인에서 제거)
 * @param monthlyBudgetMax ④ 월 예산 상한(KRW, 0 이상 정수)
 * @param arcStatus ⑤ ARC 발급 여부(단일, 필수)
 */
public record DiagnosisRequest(
    @NotNull Region region,
    @NotEmpty Set<Purpose> purposes,
    @Size(max = 3, message = "최대 3개까지 선택할 수 있습니다.") Set<DiagnosisCondition> conditions,
    @PositiveOrZero @Min(0) int monthlyBudgetMax,
    @NotNull ArcStatus arcStatus) {}
