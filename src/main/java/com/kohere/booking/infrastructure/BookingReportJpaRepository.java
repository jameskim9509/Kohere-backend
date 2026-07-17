package com.kohere.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code BookingReportRepository}의 어댑터가 사용한다. */
interface BookingReportJpaRepository extends JpaRepository<BookingReportJpaEntity, Long> {}
