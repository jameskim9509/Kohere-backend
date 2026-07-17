package com.kohere.booking.application.dto;

import java.time.Instant;

/**
 * 예약 신고 접수 응답. {@code reporterId}·{@code detail} 원문은 노출하지 않는다(프라이버시). {@code reason}은 없이 신고했으면
 * {@code null}이다. docs/api/specs/04-booking-inquiry-chat.md §6.
 */
public record BookingReportResponse(
    long reportId, long bookingId, String reason, Instant createdAt) {}
