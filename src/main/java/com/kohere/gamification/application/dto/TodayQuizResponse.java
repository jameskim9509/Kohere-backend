package com.kohere.gamification.application.dto;

import com.kohere.gamification.domain.QuizChoice;
import java.util.List;
import java.util.Optional;

/**
 * 오늘의 퀴즈 조회 응답 DTO. 미제출이면 정답·해설을 가린 문제만, 이미 제출했으면 제출 결과({@code result})를 함께 담는다.
 *
 * <p>{@code result}는 제출을 마친 사용자에게만 내려가므로 {@link Optional}로 표현한다. docs/api/specs/06-gamification.md
 * §1.
 */
public record TodayQuizResponse(
    Long quizId,
    String quizDate,
    String question,
    List<QuizChoice> choices,
    boolean submitted,
    Optional<QuizSubmissionResponse> result) {}
