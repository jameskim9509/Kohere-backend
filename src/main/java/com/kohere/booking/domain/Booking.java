package com.kohere.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 예약(신청) 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>동일 세입자·동일 방 상품 예약은 1건만 허용한다(UNIQUE {@code (tenant_id, room_offer_id)} · 재신청 시 409). 상태
 * 전이(수락/거절/취소)가 미구현이라 전 예약이 활성이므로 조건 없는 유니크로 규칙이 정확히 표현된다. 예약은 세입자 전용이며, 신청 직후 상태는 {@link
 * BookingStatus#REQUESTED} 고정이다(수락/거절/취소 등 상태 전이는 범위 밖). 생성 시 매물 소유자({@code listing.landlordId})를
 * {@code landlordId}로 비정규화 저장해 임대인 받은 신청 조회(US-4-6, userType 분기)의 소유권 스코프로 쓴다(cross-store 조인 없음 ·
 * ADR-0005). docs/api/specs/04-booking-inquiry-chat.md.
 */
@Getter
@Builder
public class Booking {

  private final Long id;
  private final Long tenantId;
  private final String listingId;
  private final String roomOfferId;
  private final Long landlordId;
  private final LocalDate moveInDate;
  private final int contractPeriod;
  private final BookingStatus status;
  private final Instant createdAt;
}
