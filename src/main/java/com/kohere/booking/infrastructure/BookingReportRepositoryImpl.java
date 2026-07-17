package com.kohere.booking.infrastructure;

import com.kohere.booking.domain.BookingReport;
import com.kohere.booking.domain.BookingReportAlreadyExistsException;
import com.kohere.booking.domain.BookingReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * 예약 신고 영속 어댑터. 도메인 포트 {@link BookingReportRepository}를 구현하고 Spring Data JPA에 위임한다. 저장 시 유니크 {@code
 * (reporter_id, booking_id)} 위반을 도메인 예외로 변환해 동시성 경합에서도 409로 응답하게 한다(사전 조회만으로는 경합에서 샌다).
 */
@Repository
@RequiredArgsConstructor
public class BookingReportRepositoryImpl implements BookingReportRepository {

  private final BookingReportJpaRepository jpaRepository;

  @Override
  public BookingReport save(BookingReport report) {
    try {
      return toDomain(jpaRepository.saveAndFlush(toEntity(report)));
    } catch (DataIntegrityViolationException e) {
      throw new BookingReportAlreadyExistsException();
    }
  }

  @Override
  public boolean existsByReporterIdAndBookingId(long reporterId, long bookingId) {
    return jpaRepository.existsByReporterIdAndBookingId(reporterId, bookingId);
  }

  private static BookingReport toDomain(BookingReportJpaEntity e) {
    return BookingReport.builder()
        .id(e.getId())
        .reporterId(e.getReporterId())
        .bookingId(e.getBookingId())
        .reason(e.getReason())
        .detail(e.getDetail())
        .createdAt(e.getCreatedAt())
        .build();
  }

  private static BookingReportJpaEntity toEntity(BookingReport r) {
    return BookingReportJpaEntity.builder()
        .id(r.getId())
        .reporterId(r.getReporterId())
        .bookingId(r.getBookingId())
        .reason(r.getReason())
        .detail(r.getDetail())
        .createdAt(r.getCreatedAt())
        .build();
  }
}
