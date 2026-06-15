package com.kohere.listing.domain;

import java.util.Optional;

/**
 * 매물 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 필터·정렬, 지도(bbox/반경), 키워드 검색 쿼리 메서드를 추가한다.
 */
public interface ListingRepository {

  Optional<Listing> findById(Long listingId);
}
