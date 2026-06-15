package com.kohere.report.domain;

import java.util.Optional;

/**
 * 신고 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 신고 저장(save), 운영자 검토용 목록 조회 메서드를 추가한다.
 */
public interface ReportRepository {

  Optional<Report> findByReporterIdAndTargetTypeAndTargetId(
      Long reporterId, ReportTargetType targetType, Long targetId);
}
