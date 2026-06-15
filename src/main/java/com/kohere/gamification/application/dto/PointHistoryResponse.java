package com.kohere.gamification.application.dto;

import com.kohere.gamification.domain.PointReason;

/**
 * 포인트 적립 내역 항목 DTO. {@code amount}는 적립 포인트 정수(양수)이며 {@code reason}은 적립 사유 enum이다.
 * docs/api/specs/06-gamification.md §4.
 */
public record PointHistoryResponse(
    Long historyId, int amount, PointReason reason, String createdAt) {}
