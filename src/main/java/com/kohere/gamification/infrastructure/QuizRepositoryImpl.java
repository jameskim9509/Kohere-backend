package com.kohere.gamification.infrastructure;

import com.kohere.gamification.domain.Quiz;
import com.kohere.gamification.domain.QuizRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 퀴즈 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link QuizRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class QuizRepositoryImpl implements QuizRepository {

  @Override
  public Optional<Quiz> findByQuizDate(LocalDate date) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
