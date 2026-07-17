package com.kohere.booking.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * 예약 신고 요청 바디. {@code reason}·{@code detail} 모두 선택이다 — reason 없이 신고해도 접수된다(사유는 {@code
 * BookingReportReason} 문자열, 미정의 값은 400 INVALID_INPUT). {@code detail}은 자유 입력(최대 500자, 응답 비노출).
 * docs/api/specs/04-booking-inquiry-chat.md §6.
 */
public record BookingReportRequest(String reason, @Size(max = 500) String detail) {}
