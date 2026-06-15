package com.kohere.listing.infrastructure;

import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 매물 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link ListingRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class ListingRepositoryImpl implements ListingRepository {

  @Override
  public Optional<Listing> findById(Long listingId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
