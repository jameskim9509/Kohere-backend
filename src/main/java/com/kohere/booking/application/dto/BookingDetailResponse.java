package com.kohere.booking.application.dto;

import com.kohere.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 예약 단건 상세 DTO. 매물 정보·가격·예약자 성명은 스냅샷이 아니라 조회 시점에 {@code listing :: api}·{@code user :: api}로 실시간
 * 조인한다(가격 변경 시 현재가 기준). 총 금액 {@code totalAmount = deposit + monthlyRent × contractPeriod}이며 관리비는
 * 제외한다. docs/api/specs/04-booking-inquiry-chat.md §3.
 */
public record BookingDetailResponse(
    Long bookingId,
    BookingStatus status,
    String listingId,
    String roomOfferId,
    String title,
    String thumbnailUrl,
    String address,
    String roomOfferName,
    Instant createdAt,
    LocalDate moveInDate,
    int contractPeriod,
    String tenantName,
    int deposit,
    int totalAmount) {}
