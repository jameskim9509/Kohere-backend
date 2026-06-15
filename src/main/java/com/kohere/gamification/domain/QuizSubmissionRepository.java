package com.kohere.gamification.domain;

import java.util.Optional;

/**
 * 퀴즈 제출 영속 포트. {@code (userId, quizId)} 단위로 제출 여부·결과를 조회한다(하루 1회 제한 판정용).
 *
 * <p>TODO: 저장 시 {@code (userId, quizDate)} 유니크 제약으로 동시 중복 제출을 차단하는 메서드를 추가한다.
 */
public interface QuizSubmissionRepository {

  Optional<QuizSubmission> findByUserIdAndQuizId(Long userId, Long quizId);
}
