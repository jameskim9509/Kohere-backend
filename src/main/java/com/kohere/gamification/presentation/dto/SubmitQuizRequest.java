package com.kohere.gamification.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 퀴즈 정답 제출 요청 바디. {@code selectedChoice}는 보기 키 {@code A}/{@code B}/{@code C}/{@code D} 중 하나(단일
 * 대문자)여야 한다. 그 외 값·빈 값·누락 시 {@code INVALID_INPUT}.
 *
 * <p>docs/api/specs/06-gamification.md §2.
 */
public record SubmitQuizRequest(@NotBlank @Pattern(regexp = "[A-D]") String selectedChoice) {}
