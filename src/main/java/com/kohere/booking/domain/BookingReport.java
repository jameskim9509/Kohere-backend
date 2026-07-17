package com.kohere.booking.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 예약 신고 접수 기록 애그리거트 루트(불변). 범위는 접수(capture)까지이며 운영자 검토·상태 전이가 없어 상태 필드를 두지 않는다. 비즈니스 키는 {@code
 * (reporterId, bookingId)}로 동일 신고자의 동일 예약 중복 신고를 막는다.
 *
 * <p>{@code reason}은 신고 사유 카탈로그({@link ReportReason})의 {@code code} 값을 참조로 보유한다(선택 · nullable).
 * report 모듈의 콘텐츠 신고(게시글·댓글·메시지)와 대상이 겹치지 않는 별개 애그리거트다 — 예약 신고 접수는 대상 존재·참여자 권한 검증이 필요해 예약을 소유한
 * booking이 담당한다. docs/architecture/domain-model.md §5.
 */
@Getter
@Builder
public class BookingReport {

  private final Long id;
  private final Long reporterId;
  private final Long bookingId;
  private final String reason;
  private final String detail;
  private final Instant createdAt;

  /** 신고 접수 팩토리 — reason(사유 코드)·detail은 선택(nullable), 식별자는 저장 시 채워진다. */
  public static BookingReport create(
      long reporterId, long bookingId, String reason, String detail, Instant now) {
    return BookingReport.builder()
        .reporterId(reporterId)
        .bookingId(bookingId)
        .reason(reason)
        .detail(detail)
        .createdAt(now)
        .build();
  }
}
