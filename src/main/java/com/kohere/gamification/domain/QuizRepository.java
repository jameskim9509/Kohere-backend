package com.kohere.gamification.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 퀴즈 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 제출 대상 퀴즈를 ID로 조회하는 메서드를 추가한다(quizId 검증·오늘 퀴즈 여부 판정용).
 */
public interface QuizRepository {

  Optional<Quiz> findByQuizDate(LocalDate date);
}
