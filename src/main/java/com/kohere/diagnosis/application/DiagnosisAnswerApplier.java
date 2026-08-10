package com.kohere.diagnosis.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.Diagnosis;
import com.kohere.diagnosis.domain.DiagnosisCondition;
import com.kohere.diagnosis.domain.District;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import com.kohere.diagnosis.domain.UniversityGroup;
import com.kohere.diagnosis.presentation.dto.AnswerRequest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 단계별 답(field+code/codes/min-max)을 파싱·검증해 진행 중 진단 초안에 적용한다. v1 단계 저장({@code POST /answers})과 v2 서버
 * 주도 흐름({@code POST /api/v2/diagnoses/next})이 공유하는 순수 컴포넌트로, 분기·파생 규칙을 한 곳에 둬 두 흐름의 정합을 유지한다(중복
 * 방지). 잘못된 답은 공통 {@code INVALID_INPUT}(400)으로 막는다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md · ADR-0036.
 */
@Component
public class DiagnosisAnswerApplier {

  /** 단일 선택 답을 싣는 요청 본문 필드명({@code AnswerRequest.code}) — {@code errors[].field}로 나간다. */
  private static final String CODE = "code";

  /** 다중 선택 답을 싣는 요청 본문 필드명({@code AnswerRequest.codes}) — {@code errors[].field}로 나간다. */
  private static final String CODES = "codes";

  /** 현재 답 1개를 초안에 적용한 새 초안을 반환한다(불변 애그리거트 전이). */
  Diagnosis apply(Diagnosis draft, AnswerRequest request) {
    String field = request.field();
    if (field == null || field.isBlank()) {
      throw new InvalidInputException("field", "필요합니다.");
    }
    Diagnosis.DiagnosisBuilder builder = draft.toBuilder();
    switch (field) {
      case "region" -> builder.region(parseEnum(Region.class, CODE, requireCode(request)));
      case "purpose" ->
          builder
              .purpose(parseEnum(Purpose.class, CODE, requireCode(request)))
              .university(null)
              .district(null);
      case "university" -> {
        requirePurpose(draft, Purpose.STUDY, "university");
        builder
            .university(parseEnum(UniversityGroup.class, CODE, requireCode(request)))
            .district(null);
      }
      case "district" -> {
        requirePurpose(draft, Purpose.NON_STUDY, "district");
        builder.district(parseEnum(District.class, CODE, requireCode(request))).university(null);
      }
      case "conditions" ->
          builder.conditions(withArcCondition(parseConditions(request), draft.getArcStatus()));
      case "monthlyRent" -> {
        validateRent(request);
        builder.monthlyRentMin(request.min()).monthlyRentMax(request.max());
      }
      case "arcStatus" -> {
        ArcStatus arcStatus = parseEnum(ArcStatus.class, CODE, requireCode(request));
        builder.arcStatus(arcStatus).conditions(withArcCondition(draft.getConditions(), arcStatus));
      }
      default -> throw new InvalidInputException("field", "지원하지 않는 값입니다: " + field);
    }
    return builder.build();
  }

  private static String requireCode(AnswerRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new InvalidInputException(CODE, "필요합니다.");
    }
    return request.code();
  }

  private static void requirePurpose(Diagnosis draft, Purpose expected, String field) {
    if (draft.getPurpose() != expected) {
      throw new InvalidInputException(
          "field", field + "는 purpose=" + expected.name() + "일 때만 저장할 수 있습니다.");
    }
  }

  private static Set<DiagnosisCondition> parseConditions(AnswerRequest request) {
    Set<DiagnosisCondition> result = new LinkedHashSet<>();
    if (request.codes() != null) {
      for (String code : request.codes()) {
        DiagnosisCondition condition = parseEnum(DiagnosisCondition.class, CODES, code);
        if (!condition.userSelectable()) {
          // NO_ARC는 ⑥ arcStatus에서 서버가 파생하는 필터라 ④에서 직접 선택할 수 없다.
          throw new InvalidInputException(CODES, "선택할 수 없는 값입니다: " + code);
        }
        result.add(condition);
      }
    }
    if (result.size() > Diagnosis.MAX_CONDITIONS) {
      throw new InvalidInputException(
          CODES, "최대 " + Diagnosis.MAX_CONDITIONS + "개까지 선택할 수 있습니다: " + result.size() + "개");
    }
    return result;
  }

  /**
   * ④ 주거 조건에 ⑥ {@code arcStatus} 파생 조건({@code NO_ARC})을 합친다. ARC 미발급({@code ArcStatus.NO_ARC})이면
   * {@code DiagnosisCondition.NO_ARC}를 넣어 ARC 불요 매물만 추천되게 하고, 발급 완료({@code ARC_ISSUED})·미응답이면 뺀다.
   * ④·⑥ 답 저장 순서와 무관하게 정합을 유지하도록 기존 파생 {@code NO_ARC}는 걷어내고 현재 {@code arcStatus} 기준으로 다시 계산한다.
   */
  private static Set<DiagnosisCondition> withArcCondition(
      Set<DiagnosisCondition> conditions, ArcStatus arcStatus) {
    Set<DiagnosisCondition> result = new LinkedHashSet<>();
    if (conditions != null) {
      conditions.stream().filter(DiagnosisCondition::userSelectable).forEach(result::add);
    }
    if (arcStatus == ArcStatus.NO_ARC) {
      result.add(DiagnosisCondition.NO_ARC);
    }
    return result;
  }

  private static void validateRent(AnswerRequest request) {
    // 두 필드를 따로 검사한다 — 「min·max 중 하나」로 묶으면 어느 쪽이 잘못됐는지 errors[]에 실을 수 없다.
    // 거절되는 요청 집합은 묶어서 검사할 때와 같다.
    if (request.min() == null) {
      throw new InvalidInputException("min", "monthlyRent 단계에서는 필수입니다.");
    }
    if (request.max() == null) {
      throw new InvalidInputException("max", "monthlyRent 단계에서는 필수입니다.");
    }
    if (request.min() < 0) {
      throw new InvalidInputException("min", "0 이상이어야 합니다: " + request.min());
    }
    if (request.max() < 0) {
      throw new InvalidInputException("max", "0 이상이어야 합니다: " + request.max());
    }
    if (request.min() > request.max()) {
      throw new InvalidInputException(
          "min", "max 이하여야 합니다: min=" + request.min() + ", max=" + request.max());
    }
  }

  /**
   * @param field 값을 실어 보낸 요청 본문 필드명({@code code} 또는 {@code codes}) — {@code errors[].field}로 나간다
   */
  private static <E extends Enum<E>> E parseEnum(Class<E> type, String field, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidInputException(field, type.getSimpleName() + " 값이 올바르지 않습니다: " + value);
    }
  }
}
