package com.kohere.gamification.infrastructure;

import com.kohere.gamification.domain.PointHistory;
import com.kohere.gamification.domain.PointHistoryRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 포인트 적립 내역 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link PointHistoryRepository}를 구현한다. 현재는 미구현이며 JPA
 * 어댑터로 교체한다.
 */
@Repository
public class PointHistoryRepositoryImpl implements PointHistoryRepository {

  @Override
  public List<PointHistory> findByUserId(Long userId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
