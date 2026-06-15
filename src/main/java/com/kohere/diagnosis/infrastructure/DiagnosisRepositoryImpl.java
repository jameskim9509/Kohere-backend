package com.kohere.diagnosis.infrastructure;

import com.kohere.diagnosis.domain.Diagnosis;
import com.kohere.diagnosis.domain.DiagnosisRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 진단 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link DiagnosisRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class DiagnosisRepositoryImpl implements DiagnosisRepository {

  @Override
  public Optional<Diagnosis> findById(Long id) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
