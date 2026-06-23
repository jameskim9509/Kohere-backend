package com.kohere.diagnosis.presentation.dto;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.District;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import com.kohere.diagnosis.domain.University;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 진단 제출 요청 바디. 6단계 진단 입력을 담는다. 형식 검증은 표현 계층(Bean Validation)에서 처리하고, 위반은 전역 핸들러가 {@code
 * INVALID_INPUT}(400) + {@code errors[]}로 변환한다.
 *
 * <p>입국 목적 조건부 필수: {@code purpose=STUDY} → {@code university} 필수·{@code district} 없음, {@code
 * purpose=NON_STUDY} → {@code district} 필수·{@code university} 없음. 위반은 공통 {@code INVALID_INPUT}.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §1.
 *
 * @param region ① 지역(단일, 필수)
 * @param purpose ② 입국 목적(단일, 필수. 유학 여부)
 * @param university ③ 대학(STUDY일 때 필수, NON_STUDY일 때 없음)
 * @param district ③ 지역·자치구(NON_STUDY일 때 필수, STUDY일 때 없음)
 * @param conditions ④ 주거 환경 조건(다중, 최대 3개. 중복은 도메인에서 제거)
 * @param monthlyBudgetMax ⑤ 월 예산 상한(KRW, 0 이상 정수)
 * @param arcStatus ⑥ ARC 발급 여부(단일, 필수)
 */
public record DiagnosisRequest(
    @NotNull Region region,
    @NotNull Purpose purpose,
    University university,
    District district,
    @Size(max = 3, message = "최대 3개까지 선택할 수 있습니다.") Set<DiagnosisCondition> conditions,
    @PositiveOrZero @Min(0) int monthlyBudgetMax,
    @NotNull ArcStatus arcStatus) {

  /**
   * 입국 목적별 대학·지역 조건부 필수를 검증한다. purpose가 비어 있으면(별도 {@code @NotNull}이 처리) true로 가드한다. STUDY는
   * university만, NON_STUDY는 district만 허용한다.
   */
  @AssertTrue(message = "입국 목적에 따라 대학(STUDY) 또는 지역(NON_STUDY) 중 하나만 선택해야 합니다.")
  public boolean isUniversityDistrictConsistent() {
    if (purpose == null) {
      return true;
    }
    return switch (purpose) {
      case STUDY -> university != null && district == null;
      case NON_STUDY -> district != null && university == null;
    };
  }
}
