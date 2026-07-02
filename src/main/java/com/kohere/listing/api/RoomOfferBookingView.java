package com.kohere.listing.api;

import java.time.LocalDate;

/**
 * booking 모듈이 예약 조회 시점에 매물·방 상품 요약과 가격을 조합하기 위한 published view. 원시 타입만 노출하고 내부 도메인 타입/enum은 공유하지
 * 않는다(모듈 경계 · domain-model §1·§2). 금액은 KRW 정수. docs/api/specs/04-booking-inquiry-chat.md.
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
    LocalDate nextAvailableFrom) {}
