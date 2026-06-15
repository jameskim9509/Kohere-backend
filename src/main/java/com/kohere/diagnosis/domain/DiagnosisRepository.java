package com.kohere.diagnosis.domain;

import java.util.Optional;

/**
 * 진단 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 사용자별 진단 이력(페이지), 최근 진단 단건 조회 메서드를 추가한다.
 */
public interface DiagnosisRepository {

  Optional<Diagnosis> findById(Long id);
}
