package com.kohere.diagnosis.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * v2 서버 주도 진단 흐름의 진행 세션 애그리거트 루트(issue #157·ADR-0036). 사용자당 최대 1건이며, v1의 진행 중 초안({@link Diagnosis}
 * {@code IN_PROGRESS})을 공유하지 않고 별도로 둔다 — {@code cursor}·{@code state} 같은 절차 필드가 {@code Diagnosis}에
 * 없고 "사용자당 IN_PROGRESS 1건" 제약과 충돌하기 때문이다.
 *
 * <p>{@code draft}에 단계별 답을 채워 가다가 {@code cursor}가 6에 도달하면 응용 계층이 {@code draft.complete(now)}로 정본
 * {@link Diagnosis}를 확정해 {@code diagnoses}에 저장하고 세션은 삭제한다. 영속(MongoDB) 매핑은 infrastructure 어댑터가
 * 처리한다.
 */
@Getter
@Builder(toBuilder = true)
public class DiagnosisFlowSession {

  private final String id;
  private final Long userId;
  private final Diagnosis draft;
  private final int cursor;
  private final FlowState state;

  /** 새 진행 세션을 시작한다(빈 초안, cursor=0, IN_FLOW). id는 영속 시 부여한다. */
  public static DiagnosisFlowSession start(long userId) {
    return DiagnosisFlowSession.builder()
        .userId(userId)
        .draft(Diagnosis.startInProgress(userId))
        .cursor(0)
        .state(FlowState.IN_FLOW)
        .build();
  }

  /** 현재 슬롯 답을 적용한 새 초안으로 교체한 세션. */
  public DiagnosisFlowSession withDraft(Diagnosis newDraft) {
    return toBuilder().draft(newDraft).build();
  }

  /** 진행 위치를 한 슬롯 전진한 세션(cursor+1). */
  public DiagnosisFlowSession advanceCursor() {
    return toBuilder().cursor(cursor + 1).build();
  }

  /** ① 지역 0건 예외질문 대기 상태로 전이한 세션(cursor는 유지). */
  public DiagnosisFlowSession awaitRegionRetry() {
    return toBuilder().state(FlowState.AWAITING_REGION_RETRY).build();
  }

  /** 지역부터 재시작: 초안을 비우고 cursor=0·IN_FLOW로 되돌린 세션(예외질문 "예"). */
  public DiagnosisFlowSession resetToRegion() {
    return toBuilder()
        .draft(Diagnosis.startInProgress(userId))
        .cursor(0)
        .state(FlowState.IN_FLOW)
        .build();
  }
}
