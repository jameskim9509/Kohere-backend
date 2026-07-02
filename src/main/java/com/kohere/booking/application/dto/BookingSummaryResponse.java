package com.kohere.booking.application.dto;

import com.kohere.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 예약 목록 항목 DTO. 매물 요약({@code title}·{@code thumbnailUrl})은 조회 시점에 {@code listing :: api}로 실시간
 * 조인한다(매물이 삭제/비공개면 null). docs/api/specs/04-booking-inquiry-chat.md §2.
 */
public record BookingSummaryResponse(
    Long bookingId,
    String listingId,
    String title,
    String thumbnailUrl,
    String roomOfferId,
    LocalDate moveInDate,
    int contractPeriod,
    BookingStatus status,
    Instant createdAt) {}
