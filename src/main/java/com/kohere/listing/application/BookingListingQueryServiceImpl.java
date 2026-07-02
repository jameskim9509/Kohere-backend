package com.kohere.listing.application;

import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@link BookingListingQueryService} 스텁 구현(TODO — listing 담당자가 채운다).
 *
 * <p>실제 구현: MongoDB에서 공개(PUBLISHED) 매물의 활성(ACTIVE) 방 상품을 {@code (listingId, roomOfferId)}로 찾아
 * 요약(title·thumbnailUrl·address·roomOffer name)·가격(deposit·monthlyRent)·입주 가능일(nextAvailableFrom)을
 * {@link RoomOfferBookingView}로 매핑한다(부재·비공개면 {@code Optional.empty()}). {@code
 * ListingRepository.findPublished}(status=PUBLISHED AND roomOffer status=ACTIVE) 기준을 따른다.
 */
@Service
public class BookingListingQueryServiceImpl implements BookingListingQueryService {

  @Override
  public Optional<RoomOfferBookingView> findPublishedRoomOffer(
      String listingId, String roomOfferId) {
    // TODO(listing 담당): (listingId, roomOfferId) 공개 방 상품 조회 후 RoomOfferBookingView 매핑.
    throw new UnsupportedOperationException(
        "TODO: listing 모듈에서 공개 매물·활성 방 상품 (listingId, roomOfferId) 조회 구현 필요");
  }
}
