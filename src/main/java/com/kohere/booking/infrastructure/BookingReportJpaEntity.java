package com.kohere.booking.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 신고 JPA 엔티티(내부, MySQL {@code booking_reports}). 도메인 {@link
 * com.kohere.booking.domain.BookingReport}과 분리해 영속 매핑만 담당한다. reason은 enum 문자열(UPPER_SNAKE), detail은
 * TEXT. 스키마는 Flyway {@code V16__create_booking_reports.sql}.
 */
@Entity
@Table(name = "booking_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingReportJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long reporterId;
  private Long bookingId;

  // 신고 사유 카탈로그(booking_report_reasons)의 code 값을 참조로 저장한다(선택 · FK 없음).
  @Column(length = 32)
  private String reason;

  @Column(columnDefinition = "TEXT")
  private String detail;

  private Instant createdAt;
}
