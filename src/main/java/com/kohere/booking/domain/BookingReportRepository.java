package com.kohere.booking.domain;

/**
 * 예약 신고 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 */
public interface BookingReportRepository {

  /** 신고 1건을 저장한다. 동일 신고자·동일 예약을 여러 번 신고할 수 있다(다건 허용 · 도배 방지는 후속 레이트리밋). */
  BookingReport save(BookingReport report);
}
