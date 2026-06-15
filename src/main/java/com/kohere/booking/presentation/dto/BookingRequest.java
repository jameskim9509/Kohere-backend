package com.kohere.booking.presentation.dto;

import com.kohere.booking.domain.ContractPeriod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 매물 신청(예약 생성) 요청 바디. 입력 형식 검증은 표현 계층에서 수행한다(docs/convention/code-style.md §3-3).
 *
 * <p>docs/api/specs/04-booking-inquiry-chat.md 신청(예약 생성) Request Body. {@code moveInDate}의 과거/입주
 * 가능일 이전 검증은 도메인 규칙이므로 응용·도메인 계층에서 {@code BOOKING_INVALID_MOVE_IN_DATE}로 처리한다.
 */
public record BookingRequest(
    @NotNull LocalDate moveInDate,
    @NotNull ContractPeriod contractPeriod,
    @Size(max = 500) String message) {}
