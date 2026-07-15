package com.kohere.diagnosis.domain;

/**
 * v2 서버 주도 진단 흐름 세션의 절차 상태(issue #157·ADR-0036).
 *
 * <ul>
 *   <li>{@code IN_FLOW} — 일반 진행 중(다음 슬롯 질문을 받는 상태).
 *   <li>{@code AWAITING_REGION_RETRY} — ① 지역 답 직후 매칭 매물이 0건이라 "다른 지역?" 예외질문의 예/아니오 응답을 기다리는 상태.
 * </ul>
 */
public enum FlowState {
  IN_FLOW,
  AWAITING_REGION_RETRY
}
