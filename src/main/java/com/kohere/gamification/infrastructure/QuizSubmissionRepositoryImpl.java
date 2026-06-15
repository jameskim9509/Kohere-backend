package com.kohere.gamification.infrastructure;

import com.kohere.gamification.domain.QuizSubmission;
import com.kohere.gamification.domain.QuizSubmissionRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 퀴즈 제출 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link QuizSubmissionRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다.
 */
@Repository
public class QuizSubmissionRepositoryImpl implements QuizSubmissionRepository {

  @Override
  public Optional<QuizSubmission> findByUserIdAndQuizId(Long userId, Long quizId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
