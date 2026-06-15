package com.kohere.gamification.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 퀴즈 제출 결과. {@code (userId, quizDate)} 유니크 제약으로 하루 1회만 성공한다(동시 중복 제출 차단). 채점·적립은 단일 트랜잭션에서 처리한다.
 * docs/api/specs/06-gamification.md.
 */
@Getter
@Builder
public class QuizSubmission {

  private final Long id;
  private final Long userId;
  private final Long quizId;
  private final String selectedChoice;
  private final boolean correct;
  private final int earnedPoint;
  private final Instant submittedAt;
}
