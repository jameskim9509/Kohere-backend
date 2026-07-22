package com.kohere.diagnosis.domain;

import java.util.Optional;

/**
 * v2 진행 세션 영속 포트(도메인). 구현은 infrastructure의 MongoDB({@code diagnosisFlowSessions}) 어댑터가 제공한다(의존성 역전,
 * docs/convention/code-style.md §3-3).
 *
 * <p><b>신원이 둘이다</b>(#181) — 회원 세션은 {@code userId}로, 비회원(게스트) 세션은 {@code guestSessionId}로 식별하며 한
 * 세션에는 정확히 하나만 채워진다. 그래서 조회·삭제가 신원 종류별로 짝을 이룬다. UNIQUE 제약도 신원별 partial 인덱스 둘로 나뉘어 있다(회원은 "사용자당 1
 * 세션", 게스트는 "키당 1 세션").
 */
public interface DiagnosisFlowSessionRepository {

  /** 사용자의 진행 세션 조회(없으면 empty). */
  Optional<DiagnosisFlowSession> findByUserId(long userId);

  /** 게스트 세션 키로 진행 세션 조회(없으면 empty). 키가 남의 것이거나 만료됐으면 empty라 남의 세션에 닿지 않는다. */
  Optional<DiagnosisFlowSession> findByGuestSessionId(String guestSessionId);

  /** 진행 세션 저장(신규는 id 부여, 기존은 갱신). 게스트 세션의 <b>생성</b>도 이 경로다 — 키가 매번 새것이라 교체할 대상이 없다. */
  DiagnosisFlowSession save(DiagnosisFlowSession session);

  /**
   * 사용자의 진행 세션을 주어진 상태로 덮어쓴다(없으면 생성). 회원이 흐름을 처음부터 시작할 때 쓴다 — "사용자당 1 세션" UNIQUE와 <b>같은
   * 조건(userId)</b>으로 원자적 upsert하므로, 삭제 후 삽입과 달리 동시 호출(시작 버튼 더블탭)에도 중복 키로 실패하지 않는다.
   *
   * <p><b>회원 전용이다.</b> 게스트는 {@code /start}마다 새 키를 발급받아 덮어쓸 이전 세션이 특정되지 않으므로 {@link #save}로 새로
   * 만든다(이전 게스트 세션은 교체되지 않고 남는다).
   *
   * <p>덮어쓰는 이전 세션은 <b>그냥 버린다</b> — 답하다 이탈한 시도는 기록하지 않는다(ADR-0036 결정 12). 이탈은 요청으로 오지 않아 돌아왔을 때에야 알
   * 수 있고, 그러면 영영 안 돌아온 사용자는 빠지고 시각도 실제 이탈 시각이 아니라 재시작 시각이 된다 — 부정확한 데이터라 남기지 않는다.
   */
  DiagnosisFlowSession upsertByUserId(DiagnosisFlowSession session);

  /** 사용자의 진행 세션 삭제(흐름 종료·완료 시). */
  void deleteByUserId(long userId);

  /** 게스트 세션 키로 진행 세션 삭제(흐름 종료·완료 시). */
  void deleteByGuestSessionId(String guestSessionId);
}
