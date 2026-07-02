package com.kohere.booking.presentation;

import com.kohere.booking.application.BookingService;
import com.kohere.booking.application.dto.BookingResponse;
import com.kohere.booking.presentation.dto.BookingRequest;
import com.kohere.common.response.ApiResponse;
import com.kohere.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매물 예약(신청) 생성 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다(docs/convention/code-style.md §3-3).
 * 신청은 매물에 종속되는 액션이라 {@code /listings/{listingId}} 하위에 중첩한다.
 *
 * <p>스펙: docs/api/specs/04-booking-inquiry-chat.md §1. 성공 시 {@code 201 Created} + {@code Location:
 * /api/v1/bookings/{bookingId}}. 조회(목록·상세)는 {@link MyBookingController} 참조.
 */
@RestController
@RequestMapping("/api/v1/listings/{listingId}")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;

  @PostMapping("/bookings")
  public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable String listingId,
      @Valid @RequestBody BookingRequest request) {
    BookingResponse response = bookingService.createBooking(principal.userId(), listingId, request);
    return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.bookingId()))
        .body(ApiResponse.success(response));
  }
}
