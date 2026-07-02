package com.kohere.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 예약(신청) 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>MVP의 예약은 "신청" 성격이라 중복 제한이 없다 — 같은 세입자·방 상품에도 여러 신청을 append한다. 예약은 세입자 전용이며, 신청 직후 상태는 {@link
 * BookingStatus#REQUESTED} 고정이다(수락/거절/취소 등 상태 전이는 범위 밖). docs/api/specs/04-booking-inquiry-chat.md.
 */
@Getter
@Builder
public class Booking {

  private final Long id;
  private final Long tenantId;
  private final String listingId;
  private final String roomOfferId;
  private final LocalDate moveInDate;
  private final int contractPeriod;
  private final BookingStatus status;
  private final Instant createdAt;
}
