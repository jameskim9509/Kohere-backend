package com.kohere.gamification.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 포인트 적립 내역 1건. {@code amount}는 적립 포인트 정수(양수)이며 KRW 금액이 아니다(차감·음수는 본 범위 밖).
 * docs/api/specs/06-gamification.md.
 */
@Getter
@Builder
public class PointHistory {

  private final Long id;
  private final Long userId;
  private final int amount;
  private final PointReason reason;
  private final Instant createdAt;
}
