package com.kohere.diagnosis.application.dto;

/**
 * v2 서버 주도 진단 흐름({@code POST /api/v2/diagnoses/next})의 응답 결과코드(issue #157·ADR-0036). 정상 {@code 200}
 * 응답 {@code data}에 실리는 태그드 유니온의 태그이며 에러가 아니다({@code TERMINATED}·{@code NO_MATCH} 포함). 도메인 전이 enum
 * {@code DiagnosisStatus}와 분리한다.
 *
 * <ul>
 *   <li>{@code NEXT_QUESTION} — 다음 질문이 남음({@code cursor < 6}). {@code question} 채움.
 *   <li>{@code REGION_RETRY} — ① 지역 답 직후 매칭 0건 → 재질의(서버 합성 yes/no). {@code question} 채움.
 *   <li>{@code COMPLETED} — 빌더 완성 → 자동 확정, 매칭 있음. {@code recommendation}·{@code diagnosisId} 채움.
 *   <li>{@code NO_MATCH} — 자동 확정 후 매칭 0건(코드만, 조정 제안 없음).
 *   <li>{@code TERMINATED} — 지역 예외질문 "아니오" → 진단 종료(코드만).
 * </ul>
 */
public enum FlowResultCode {
  NEXT_QUESTION,
  REGION_RETRY,
  COMPLETED,
  NO_MATCH,
  TERMINATED
}
