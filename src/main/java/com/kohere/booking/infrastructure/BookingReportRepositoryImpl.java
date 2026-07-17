package com.kohere.booking.infrastructure;

import com.kohere.booking.domain.BookingReport;
import com.kohere.booking.domain.BookingReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 예약 신고 영속 어댑터. 도메인 포트 {@link BookingReportRepository}를 구현하고 Spring Data JPA에 위임한다. 동일 신고자·동일 예약 다건
 * 신고를 허용하므로 유니크 제약·중복 변환이 없다(도배 방지는 후속 레이트리밋).
 */
@Repository
@RequiredArgsConstructor
public class BookingReportRepositoryImpl implements BookingReportRepository {

  private final BookingReportJpaRepository jpaRepository;

  @Override
  public BookingReport save(BookingReport report) {
    return toDomain(jpaRepository.save(toEntity(report)));
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
