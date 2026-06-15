package com.kohere.gamification.application.dto;

/**
 * 퀴즈 제출(채점·적립) 결과 DTO. 정답·해설은 제출을 마친 사용자에게만 공개한다. {@code earnedPoint}는 이번 제출로 적립된 포인트, {@code
 * totalPoint}는 적립 반영 후 합계다.
 *
 * <p>docs/api/specs/06-gamification.md §2.
 */
public record QuizSubmissionResponse(
    Long quizId,
    String selectedChoice,
    boolean correct,
    String correctChoice,
    String explanation,
    int earnedPoint,
    long totalPoint,
    String submittedAt) {}
