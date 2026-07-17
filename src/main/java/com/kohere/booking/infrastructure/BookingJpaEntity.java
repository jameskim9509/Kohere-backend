package com.kohere.booking.infrastructure;

import com.kohere.booking.domain.BookingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 JPA 엔티티(내부). 도메인 {@link com.kohere.booking.domain.Booking}과 분리해 영속 매핑만 담당한다. 컬럼명은 기본
 * snake_case 매핑을 따른다(tenantId→tenant_id 등). {@code listing_id}·{@code room_offer_id}는 MongoDB
 * ObjectId 문자열 값 참조(FK 없음 · cross-store). 스키마는 Flyway {@code V9__bookings.sql}.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long tenantId;
  private String listingId;
  private String roomOfferId;
  private Long landlordId;
  private LocalDate moveInDate;
  private int contractPeriod;

  @Enumerated(EnumType.STRING)
  private BookingStatus status;

  private Instant createdAt;

  // 참여자별 소프트삭제(NULL=미삭제). 한 예약 행을 tenant·landlord가 공유하므로 요청자 쪽 컬럼만 세팅하고, 조회 시 요청자 쪽 컬럼이 NULL인
  // 예약만 노출한다. 스키마는 Flyway V14__add_bookings_deleted_at.sql.
  private Instant tenantDeletedAt;
  private Instant landlordDeletedAt;
}
