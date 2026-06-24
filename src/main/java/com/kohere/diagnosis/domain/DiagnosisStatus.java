package com.kohere.diagnosis.domain;

/**
 * 진단 상태. 진행 중 작성은 {@code IN_PROGRESS}, 제출(확정) 완료 시 {@code COMPLETED}로 전이한다(사용자당 진행 중 1건). 이력·최근 조회는
 * {@code COMPLETED}만 노출한다. docs/api/specs/02-diagnosis-recommendation.md (status).
 */
public enum DiagnosisStatus {
  IN_PROGRESS,
  COMPLETED
}
