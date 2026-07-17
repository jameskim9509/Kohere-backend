package com.kohere.booking.presentation;

import com.kohere.booking.application.BookingService;
import com.kohere.booking.application.dto.BookingReportResponse;
import com.kohere.booking.application.dto.ReportReasonResponse;
import com.kohere.booking.presentation.dto.BookingReportRequest;
import com.kohere.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예약 내역 관리 컨트롤러(/api/v1/bookings) — 삭제·차단·신고와 신고 사유 목록. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에
 * 위임한다(docs/convention/code-style.md §3-3). 조회(목록·상세)는 {@link MyBookingController} 참조.
 *
 * <p>삭제·차단은 관측 결과("내 목록에서 사라짐")로만 드러나고 응답 본문이 없다(204, 멱등). 차단 목록·해제는 예약과 무관해 {@code
 * /api/v1/users/me/blocks}(user 모듈)에 있다. 도메인 DTO만 반환하고 공통 래퍼는 {@code ApiResponseWrapper}가 자동
 * 적용한다(ADR-0013). 스펙: docs/api/specs/04-booking-inquiry-chat.md §4·§5·§6·§7.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingManagementController {

  private final BookingService bookingService;

  @DeleteMapping("/{bookingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBooking(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bookingId) {
    bookingService.deleteBooking(principal.userId(), bookingId);
  }

  @PostMapping("/{bookingId}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void blockBooking(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bookingId) {
    bookingService.blockBooking(principal.userId(), bookingId);
  }

  @PostMapping("/{bookingId}/report")
  @ResponseStatus(HttpStatus.CREATED)
  public BookingReportResponse reportBooking(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable long bookingId,
      @Valid @RequestBody BookingReportRequest request) {
    return bookingService.reportBooking(principal.userId(), bookingId, request);
  }

  @GetMapping("/report-reasons")
  public ReportReasonResponse getReportReasons(@AuthenticationPrincipal AuthPrincipal principal) {
    return bookingService.getReportReasons(principal.userId());
  }
}
