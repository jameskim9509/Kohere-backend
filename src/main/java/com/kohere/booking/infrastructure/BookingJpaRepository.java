package com.kohere.booking.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code BookingRepository}의 어댑터가 사용한다. */
interface BookingJpaRepository extends JpaRepository<BookingJpaEntity, Long> {

  Page<BookingJpaEntity> findByTenantId(Long tenantId, Pageable pageable);

  Optional<BookingJpaEntity> findByIdAndTenantId(Long id, Long tenantId);

  long countByTenantId(Long tenantId);
}
