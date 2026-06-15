package com.kohere.report.infrastructure;

import com.kohere.report.domain.Report;
import com.kohere.report.domain.ReportRepository;
import com.kohere.report.domain.ReportTargetType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 신고 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link ReportRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class ReportRepositoryImpl implements ReportRepository {

  @Override
  public Optional<Report> findByReporterIdAndTargetTypeAndTargetId(
      Long reporterId, ReportTargetType targetType, Long targetId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
