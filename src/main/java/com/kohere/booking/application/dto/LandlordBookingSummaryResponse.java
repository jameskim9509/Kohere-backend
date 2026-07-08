package com.kohere.booking.application.dto;

import com.kohere.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 임대인 받은 신청 목록 항목 DTO(임대인 분기 · US-4-6). 세입자 목록 항목({@link BookingSummaryResponse})에 방 상품명({@code
 * roomOfferName})과 신청자 성명({@code applicantName})을 더한 형태다. 매물 요약·방 상품명은 조회 시점에 {@code listing ::
 * api}로 실시간 조인하며(매물이 삭제/비공개면 null), 신청자 성명은 {@code user :: api}({@code getUserName})로 조인한다.
 * docs/api/specs/04-booking-inquiry-chat.md §2(임대인 분기).
 */
public record LandlordBookingSummaryResponse(
    Long bookingId,
    String listingId,
    String title,
    String thumbnailUrl,
    String roomOfferId,
    String roomOfferName,
    String applicantName,
    LocalDate moveInDate,
    int contractPeriod,
    BookingStatus status,
    Instant createdAt) {}
