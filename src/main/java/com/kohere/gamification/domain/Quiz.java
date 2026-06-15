package com.kohere.gamification.domain;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 오늘의 퀴즈 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다(docs/convention/code-style.md §3-3). {@code
 * correctChoice}/{@code explanation}은 정답·해설로, 제출을 마친 사용자에게만 공개한다.
 *
 * <p>TODO: 정답 판정({@code correctChoice}와 선택 보기 대조)·오늘 퀴즈 여부 검증 등 도메인 불변식 메서드를 채운다.
 *
 * <p>스펙: docs/api/specs/06-gamification.md.
 */
@Getter
@Builder
public class Quiz {

  private final Long id;
  private final LocalDate quizDate;
  private final String question;
  private final List<QuizChoice> choices;
  private final String correctChoice;
  private final String explanation;
}
