package com.kohere.listing.domain;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * 매물 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>TODO: 도메인 불변식·상태 전이(공개/비공개, 입주 가능일 등) 메서드를 채운다.
 */
@Getter
@Builder
public class Listing {

  private final Long id;
  private final String title;
  private final ListingType type;
  private final int monthlyRent;
  private final int deposit;
  private final double lat;
  private final double lng;
  private final String address;
  private final Set<ConditionTag> conditions;
  private final boolean arcRequired;
  private final Instant createdAt;
}
