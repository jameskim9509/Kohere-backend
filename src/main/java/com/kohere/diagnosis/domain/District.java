package com.kohere.diagnosis.domain;

/**
 * 진단 ③ 지역(자치구) 선택(단일). 입국 목적이 {@code NON_STUDY}일 때만 필수다.
 * docs/api/specs/02-diagnosis-recommendation.md (③ 대학/지역 선택).
 */
public enum District {
  GURO_GU,
  YEONGDEUNGPO_GU,
  GEUMCHEON_GU,
  GWANAK_GU,
  DONGDAEMUN_GU,
  ETC
}
