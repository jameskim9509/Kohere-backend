package com.kohere.gamification.domain;

import java.util.List;

/**
 * 포인트 적립 내역 영속 포트. 인증 주체별로만 필터링되어 타인 데이터를 반환하지 않는다.
 *
 * <p>TODO: 합계 집계·오프셋 페이지 조회 메서드를 추가한다(현재는 사용자별 전체 내역 조회만 둔다).
 */
public interface PointHistoryRepository {

  List<PointHistory> findByUserId(Long userId);
}
