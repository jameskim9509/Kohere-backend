package com.kohere.user.domain;

/**
 * 직업 유형(온보딩 선택 — #187에서 필수→선택 완화, 미설정은 NULL). 요구사항 정의서의 직업 드롭다운 확정 항목(#93, #138 개편).
 * docs/architecture/domain-model.md §2 · database-design §6.
 */
public enum Occupation {
  /** 학부생(undergraduate student). */
  UNDERGRADUATE_STUDENT,
  /** 대학원생(graduate student). */
  GRADUATE_STUDENT,
  /** 교환학생(exchange student). */
  EXCHANGE_STUDENT,
  /** 어학·교육(language teaching). */
  LANGUAGE_TEACHING,
  /** 제조·생산(manufacturing/production). */
  MANUFACTURING_PRODUCTION,
  /** 사업·무역(business/trade). */
  BUSINESS_TRADE,
  /** 기타(etc). */
  ETC
}
