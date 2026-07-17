package com.kohere.booking.domain;

/**
 * 예약 신고 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 */
public interface BookingReportRepository {

  /**
   * 신고를 저장한다. 동일 신고자·동일 예약 중복(유니크 {@code (reporter_id, booking_id)} 위반)은 {@link
   * BookingReportAlreadyExistsException}으로 변환한다(동시성 경합 포함).
   */
  BookingReport save(BookingReport report);

  boolean existsByReporterIdAndBookingId(long reporterId, long bookingId);
}
