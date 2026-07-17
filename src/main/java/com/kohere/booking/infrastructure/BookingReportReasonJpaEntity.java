package com.kohere.booking.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 신고 사유 카탈로그 JPA 엔티티(내부, MySQL {@code booking_report_reasons}). {@code (code, lang)} 한 쌍이 한 라벨이다
 * — 언어 추가도 행 INSERT로 한다. 스키마는 Flyway {@code V17__create_booking_report_reasons.sql}.
 */
@Entity
@Table(name = "booking_report_reasons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingReportReasonJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 32)
  private String code;

  @Column(length = 8)
  private String lang;

  @Column(length = 100)
  private String label;

  private int displayOrder;

  private boolean active;
}
