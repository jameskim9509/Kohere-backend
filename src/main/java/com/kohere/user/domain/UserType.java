package com.kohere.user.domain;

/**
 * 회원 역할. 온보딩 제출 엔드포인트로 확정되고 이후 불변이다(세입자 {@code POST /auth/onboarding}, 임대인 {@code POST
 * /auth/landlord/onboarding}). 신규 회원은 기본 {@code TENANT}이며 임대인 온보딩 시 {@code LANDLORD}로 확정한다(V8
 * DEFAULT 'TENANT'와 정합). domain-model §2 · ADR-0034.
 */
public enum UserType {

  /** 세입자(외국인 거주 탐색자). */
  TENANT,

  /** 임대인(매물 등록자). */
  LANDLORD
}
