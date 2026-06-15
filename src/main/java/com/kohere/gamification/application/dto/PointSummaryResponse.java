package com.kohere.gamification.application.dto;

/**
 * 포인트 합계 응답 DTO. {@code totalPoint}는 적립 로그 집계 결과이며 포인트 정수(KRW 금액 아님)다.
 * docs/api/specs/06-gamification.md §3.
 */
public record PointSummaryResponse(long totalPoint) {}
