package com.kohere.booking.presentation;

import com.kohere.booking.application.BookingService;
import com.kohere.booking.application.dto.BookingDetailResponse;
import com.kohere.booking.application.dto.BookingSummaryResponse;
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
 * 내 예약 조회 컨트롤러(목록·단건 상세). 요청자 본인 예약만 반환한다. 도메인 DTO만 반환하고 공통 래퍼는 {@code ApiResponseWrapper}가 자동
 * 적용한다(ADR-0013). docs/api/specs/04-booking-inquiry-chat.md §2·§3.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class MyBookingController {

  private final BookingService bookingService;

  @GetMapping
  public PageResponse<BookingSummaryResponse> getMyBookings(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return bookingService.getMyBookings(principal.userId(), page, size);
  }

  @GetMapping("/{bookingId}")
  public BookingDetailResponse getBooking(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bookingId) {
    return bookingService.getBooking(principal.userId(), bookingId);
  }
}
