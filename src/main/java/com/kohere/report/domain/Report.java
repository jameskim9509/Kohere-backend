package com.kohere.report.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 신고 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>동일 신고자·동일 대상 중복은 {@code (reporterId, targetType, targetId)} 유니크 제약으로 1건만 허용한다.
 * docs/api/specs/07-reports.md.
 *
 * <p>TODO: 신고 접수 팩토리·불변식(상태는 접수 시 항상 RECEIVED) 메서드를 채운다.
 */
@Getter
@Builder
public class Report {

  private final Long id;
  private final Long reporterId;
  private final ReportTargetType targetType;
  private final Long targetId;
  private final ReportReason reason;
  private final String detail;
  private final ReportStatus status;
  private final Instant createdAt;
}
