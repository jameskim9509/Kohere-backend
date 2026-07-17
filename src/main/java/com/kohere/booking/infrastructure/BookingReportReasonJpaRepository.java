package com.kohere.booking.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code ReportReasonRepository}의 어댑터가 사용한다. */
interface BookingReportReasonJpaRepository
    extends JpaRepository<BookingReportReasonJpaEntity, Long> {

  List<BookingReportReasonJpaEntity> findByLangAndActiveTrueOrderByDisplayOrderAsc(String lang);

  boolean existsByCodeAndActiveTrue(String code);
}
