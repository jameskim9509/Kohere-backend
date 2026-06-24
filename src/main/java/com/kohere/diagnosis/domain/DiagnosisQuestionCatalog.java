package com.kohere.diagnosis.domain;

import java.util.Optional;

/**
 * 진단 문항 카탈로그 포트(도메인). 구현은 infrastructure의 MongoDB({@code diagnosisQuestions}) 어댑터가 제공한다. 데이터만 보유하며
 * 분기 메타는 두지 않는다 — 어느 단계 문항을 낼지는 응용 서비스가 저장된 {@code purpose}로 결정한다(ADR-0028).
 */
public interface DiagnosisQuestionCatalog {

  /** 제출 필드명으로 활성 문항 1건 조회(예: {@code region}, {@code university}). */
  Optional<DiagnosisQuestion> findByField(String field);
}
