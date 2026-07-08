package com.kohere.booking.presentation;

import com.kohere.booking.application.BookingService;
import com.kohere.common.response.PageResponse;
import com.kohere.common.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예약 조회 컨트롤러(목록·단건 상세). 요청자 {@code userType}으로 분기한다 — 세입자는 내 예약을, 임대인은 자기 소유 매물(생성 시 저장된 {@code
 * landlord_id})에 신청된 예약을 반환한다. 별도 임대인 전용 API 없이 같은 경로에서 분기하며, 두 역할 모두 유효 요청이라 역할 {@code 403}은 없다.
 *
 * <p>도메인 DTO만 반환하고 공통 래퍼는 {@code ApiResponseWrapper}가 자동 적용한다(ADR-0013). 분기에 따라 응답 본문의 필드가 달라진다(세입자
 * {@code BookingSummaryResponse}/{@code BookingDetailResponse} · 임대인 {@code
 * LandlordBookingSummaryResponse}/{@code LandlordBookingDetailResponse}).
 * docs/api/specs/04-booking-inquiry-chat.md §2·§3.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class MyBookingController {

  private final BookingService bookingService;

  @GetMapping
  public PageResponse<?> getBookings(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return bookingService.getBookings(principal.userId(), page, size);
  }

  @GetMapping("/{bookingId}")
  public Object getBooking(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bookingId) {
    return bookingService.getBooking(principal.userId(), bookingId);
  }
}
