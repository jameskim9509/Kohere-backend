package com.kohere.diagnosis.domain;

/**
 * v2 서버 주도 진단 흐름의 정본 6슬롯 선형 순서(issue #157·ADR-0036). 진행 위치({@code cursor})가 이 순서를 강제한다 — {@link
 * Diagnosis#validateComplete()}가 {@code conditions}(④)를 필수로 보지 않아 완료 판정만으로는 ④를 건너뛸 수 있으므로, 다음 질문·완료
 * 판정의 단일 정본은 {@code cursor}이며 이 enum이 슬롯↔(step, field) 매핑을 고정한다.
 *
 * <p>{@code BRANCH}(step 3)만 조건부라 {@code field}가 없다 — 저장된 {@code purpose}로 {@code
 * university}/{@code district}를 서버가 택일한다(ADR-0028).
 */
public enum DiagnosisFlowStep {
  REGION(1, "region"),
  PURPOSE(2, "purpose"),
  BRANCH(3, null),
  CONDITIONS(4, "conditions"),
  MONTHLY_RENT(5, "monthlyRent"),
  ARC_STATUS(6, "arcStatus");

  private final int step;
  private final String field;

  DiagnosisFlowStep(int step, String field) {
    this.step = step;
    this.field = field;
  }

  /** 카탈로그 조회용 단계 번호(1..6). */
  public int step() {
    return step;
  }

  /** 제출 필드명. {@code BRANCH}는 {@code null}(purpose로 런타임 결정). */
  public String field() {
    return field;
  }

  /** 전체 슬롯 수(=6). {@code cursor == count()}이면 빌더 완성 → 자동 확정. */
  public static int count() {
    return values().length;
  }

  /** {@code cursor}(0-base) 위치의 슬롯. */
  public static DiagnosisFlowStep at(int cursor) {
    DiagnosisFlowStep[] values = values();
    if (cursor < 0 || cursor >= values.length) {
      throw new IllegalArgumentException("cursor가 범위를 벗어났습니다: " + cursor);
    }
    return values[cursor];
  }
}
