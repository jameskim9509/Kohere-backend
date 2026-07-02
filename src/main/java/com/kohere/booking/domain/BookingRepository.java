package com.kohere.booking.domain;

import java.util.List;
import java.util.Optional;

/**
 * 예약 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다. MVP의 예약은 "신청" 성격이라 중복 방지 유니크 제약이 없다.
 */
public interface BookingRepository {

  Booking save(Booking booking);

  /** 요청자 본인 예약을 최신순(createdAt desc)으로 오프셋 조회한다. */
  List<Booking> findByTenantId(Long tenantId, int page, int size);

  long countByTenantId(Long tenantId);

  /** 본인 예약 단건 조회. 없거나 타인 예약이면 빈 값을 반환한다(호출측이 404로 통일). */
  Optional<Booking> findByIdAndTenantId(Long id, Long tenantId);
}
