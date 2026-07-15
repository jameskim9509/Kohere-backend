package com.kohere.diagnosis.domain;

import java.util.Optional;

/**
 * v2 진행 세션 영속 포트(도메인). 구현은 infrastructure의 MongoDB({@code diagnosisFlowSessions}) 어댑터가 제공한다(의존성 역전,
 * docs/convention/code-style.md §3-3). 사용자당 1 세션(userId UNIQUE).
 */
public interface DiagnosisFlowSessionRepository {

  /** 사용자의 진행 세션 조회(없으면 empty). */
  Optional<DiagnosisFlowSession> findByUserId(long userId);

  /** 진행 세션 저장(신규는 id 부여, 기존은 갱신). */
  DiagnosisFlowSession save(DiagnosisFlowSession session);

  /** 사용자의 진행 세션 삭제(흐름 종료·완료 시). */
  void deleteByUserId(long userId);
}
