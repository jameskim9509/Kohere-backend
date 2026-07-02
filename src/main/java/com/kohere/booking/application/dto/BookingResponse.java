package com.kohere.booking.application.dto;

import com.kohere.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 예약 생성 결과 DTO(예약 코어 내역만). 매물 요약·가격·예약자 성명은 조회(상세) 시점에 실시간 조인해 내려준다.
 * docs/api/specs/04-booking-inquiry-chat.md §1.
 */
public record BookingResponse(
    Long bookingId,
    BookingStatus status,
    String listingId,
    String roomOfferId,
    LocalDate moveInDate,
    int contractPeriod,
    Instant createdAt) {}
