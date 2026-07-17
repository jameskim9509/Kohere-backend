package com.kohere.user.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 간 차단 애그리거트 루트(불변 기록). 차단은 사용자 단위(전역)다 — 임대인은 매물·방 상품을 여러 개 가지므로 예약/방 단위 차단은 상대의 다른 방으로 우회된다.
 * 비즈니스 키는 {@code (blockerId, blockedUserId)}이며 행 존재 자체가 차단을 뜻한다(활성 플래그 없음, 해제 = 행 삭제).
 *
 * <p>생성은 booking이 예약에서 상대를 도출해 {@code user :: api} 공개 명령으로 위임하고, 목록·해제는 user 모듈이 직접 제공한다.
 * docs/architecture/domain-model.md §2 UserBlock.
 */
@Getter
@Builder
public class UserBlock {

  private final Long id;
  private final Long blockerId;
  private final Long blockedUserId;
  private final Instant createdAt;

  /** 차단 접수 팩토리 — 식별자는 저장 시 채워진다. */
  public static UserBlock create(long blockerId, long blockedUserId, Instant now) {
    return UserBlock.builder()
        .blockerId(blockerId)
        .blockedUserId(blockedUserId)
        .createdAt(now)
        .build();
  }
}
