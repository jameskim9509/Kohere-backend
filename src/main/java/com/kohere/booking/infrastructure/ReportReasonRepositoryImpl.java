package com.kohere.booking.infrastructure;

import com.kohere.booking.domain.ReportReason;
import com.kohere.booking.domain.ReportReasonRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 예약 신고 사유 카탈로그 영속 어댑터. 도메인 포트 {@link ReportReasonRepository}를 구현하고 Spring Data JPA에 위임한다. 활성 사유의
 * 표준 집합·순서는 {@code en} 행으로 정하고, 요청 언어 라벨이 있으면 그 값을, 없으면 {@code en} 라벨로 폴백해 조립한다.
 */
@Repository
@RequiredArgsConstructor
public class ReportReasonRepositoryImpl implements ReportReasonRepository {

  private static final String FALLBACK_LANG = "en";

  private final BookingReportReasonJpaRepository jpaRepository;

  @Override
  public List<ReportReason> findActiveOrdered(String lang) {
    List<BookingReportReasonJpaEntity> canonical =
        jpaRepository.findByLangAndActiveTrueOrderByDisplayOrderAsc(FALLBACK_LANG);
    Map<String, String> langLabels =
        FALLBACK_LANG.equals(lang)
            ? Map.of()
            : jpaRepository.findByLangAndActiveTrueOrderByDisplayOrderAsc(lang).stream()
                .collect(
                    Collectors.toMap(
                        BookingReportReasonJpaEntity::getCode,
                        BookingReportReasonJpaEntity::getLabel,
                        (a, b) -> a));
    return canonical.stream()
        .map(e -> new ReportReason(e.getCode(), langLabels.getOrDefault(e.getCode(), e.getLabel())))
        .toList();
  }

  @Override
  public boolean existsActiveByCode(String code) {
    return jpaRepository.existsByCodeAndActiveTrue(code);
  }
}
