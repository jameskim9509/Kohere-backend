package com.kohere.listing.api;

import java.util.Optional;

/**
 * 예약(booking) 협력용 매물 공개 쿼리. 공개(PUBLISHED) 매물의 활성(ACTIVE) 방 상품을 {@code (listingId, roomOfferId)}로
 * 조회한다. 결과가 없으면(매물/방 상품 부재·비공개) 빈 값을 반환하며, 호출측(booking)이 404 {@code LISTING_NOT_FOUND}로 처리한다.
 *
 * <p>booking이 조회 시점에 매물 요약·가격·입주 가능일을 실시간 조인하는 데 쓴다(스냅샷 없음 · ADR-0002 공개 API 협력).
 */
public interface BookingListingQueryService {

  Optional<RoomOfferBookingView> findPublishedRoomOffer(String listingId, String roomOfferId);
}
