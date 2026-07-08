package com.kohere.booking.application.dto;

import com.kohere.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 임대인 받은 신청 단건 상세 DTO(임대인 분기 · US-4-6). 세입자 상세({@link BookingDetailResponse})의 {@code tenantName}
 * 대신 신청자 프로필({@code applicantId}·{@code applicantName}·{@code applicantGender}·{@code
 * applicantCountry}·{@code applicantCountryName}·{@code applicantEmail})을 담고, 금액 필드({@code
 * deposit}·{@code totalAmount})는 세입자 분기와 동일하다(총 금액 {@code = deposit + monthlyRent ×
 * contractPeriod}, 관리비 제외).
 *
 * <p>매물 정보·가격·신청자 프로필은 스냅샷이 아니라 조회 시점에 {@code listing :: api}·{@code user :: api}로 실시간 조인한다(가격 변경 시
 * 현재가 기준). 신청자 PII(이메일·성별·국적)는 마스킹 없이 평문 노출하며, 탈퇴 신청자는 익명화(ADR-0014)로 값이 비어 있을 수 있다.
 * docs/api/specs/04-booking-inquiry-chat.md §3(임대인 분기).
 */
public record LandlordBookingDetailResponse(
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
    Long applicantId,
    String applicantName,
    String applicantGender,
    String applicantCountry,
    String applicantCountryName,
    String applicantEmail,
    int deposit,
    int totalAmount) {}
