package com.kohere.listing.api;

/**
 * booking 모듈이 예약 생성·조회 시점에 매물·방 상품 요약과 가격을 조합하기 위한 published view. 원시 타입만 노출하고 내부 도메인 타입/enum은 공유하지
 * 않는다(모듈 경계 · domain-model §1·§2). 금액은 KRW 정수. docs/api/specs/04-booking-inquiry-chat.md.
 *
 * <p>{@code landlordId}는 매물 소유자(임대인) 식별자로, booking이 예약 **생성 시** {@code Booking.landlordId}로 비정규화
 * 저장해 임대인 받은 신청 조회(US-4-6)의 소유권 스코프에 쓴다(생성이 이미 매물을 조회하므로 소유자 스냅샷 캡처 비용이 거의 없다).
 */
public record RoomOfferBookingView(
    String listingId,
    String roomOfferId,
    String title,
    String thumbnailUrl,
    String address,
    String roomOfferName,
    int deposit,
    int monthlyRent,
    Long landlordId) {}
