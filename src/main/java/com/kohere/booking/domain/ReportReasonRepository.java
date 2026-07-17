package com.kohere.booking.domain;

import java.util.List;

/**
 * 예약 신고 사유 카탈로그 영속 포트. 구현은 infrastructure 계층에 둔다(docs/convention/code-style.md §3-3). 사유는 코드 배포 없이
 * 카탈로그 행 추가로 늘릴 수 있다.
 */
public interface ReportReasonRepository {

  /** 활성 사유를 표시 순서대로, 요청 언어 라벨(없으면 en 폴백)로 조회한다(신고 사유 목록용). */
  List<ReportReason> findActiveOrdered(String lang);

  /** 활성 사유 코드인지 검증한다(신고 접수 시 reason 유효성). */
  boolean existsActiveByCode(String code);
}
