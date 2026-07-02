package com.kohere.booking.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 매물 예약(신청) 요청 바디. 입력 형식 검증은 표현 계층에서 수행한다(docs/convention/code-style.md §3-3).
 * docs/api/specs/04-booking-inquiry-chat.md §1.
 *
 * <p>{@code contractPeriod}는 계약 개월수(양의 정수). {@code moveInDate}의 과거/입주 가능일 이전 검증은 도메인 규칙이라 응용 계층에서
 * {@code BOOKING_INVALID_MOVE_IN_DATE}(422)로 처리한다. 누락·범위 위반은 {@code INVALID_INPUT}(400), 타입 불일치는
 * {@code MALFORMED_REQUEST}(400).
 */
public record BookingRequest(
    @NotBlank String roomOfferId,
    @NotNull LocalDate moveInDate,
    @NotNull @Positive Integer contractPeriod) {}
