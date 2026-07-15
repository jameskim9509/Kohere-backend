package com.kohere.diagnosis.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * v2 서버 주도 진단 흐름({@code POST /api/v2/diagnoses/next}) 응답 DTO. {@code resultCode}(태그드 유니온의 태그)에 따라
 * 채워지는 payload가 다르며, 채워지지 않는 필드는 직렬화에서 생략한다({@code NON_NULL}). 정본: issue #157·ADR-0036.
 *
 * @param resultCode 결과코드(항상 존재)
 * @param question NEXT_QUESTION·REGION_RETRY일 때의 질문 1개(그 외 null)
 * @param recommendation COMPLETED일 때의 추천 결과(그 외 null)
 * @param diagnosisId COMPLETED일 때 확정된 진단 식별자(그 외 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisFlowResponse(
    FlowResultCode resultCode,
    QuestionResponse question,
    DiagnosisRecommendationView recommendation,
    Long diagnosisId) {

  public static DiagnosisFlowResponse nextQuestion(QuestionResponse question) {
    return new DiagnosisFlowResponse(FlowResultCode.NEXT_QUESTION, question, null, null);
  }

  public static DiagnosisFlowResponse regionRetry(QuestionResponse question) {
    return new DiagnosisFlowResponse(FlowResultCode.REGION_RETRY, question, null, null);
  }

  public static DiagnosisFlowResponse completed(
      Long diagnosisId, DiagnosisRecommendationView recommendation) {
    return new DiagnosisFlowResponse(FlowResultCode.COMPLETED, null, recommendation, diagnosisId);
  }

  public static DiagnosisFlowResponse noMatch() {
    return new DiagnosisFlowResponse(FlowResultCode.NO_MATCH, null, null, null);
  }

  public static DiagnosisFlowResponse terminated() {
    return new DiagnosisFlowResponse(FlowResultCode.TERMINATED, null, null, null);
  }
}
