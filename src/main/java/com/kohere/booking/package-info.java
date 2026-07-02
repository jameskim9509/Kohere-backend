/**
 * 매물 예약(신청) Bounded Context. {@code ACTIVE} 세입자가 방 상품(roomOffer)에 타겟 입주일·계약기간(개월수)으로 예약을 저장하고, 내
 * 예약을 목록·단건 상세로 조회한다. MVP의 예약은 "신청" 성격이라 중복 제한이 없다(예약은 세입자 전용).
 *
 * <p>도메인 에러 코드 prefix: {@code BOOKING}. 스펙: docs/api/specs/04-booking-inquiry-chat.md.
 *
 * <p>조회 시점에 매물 요약·가격은 {@code listing :: api}, 예약자 성명은 {@code user :: api}로 실시간 조인한다(스냅샷 없음,
 * cross-store 조인 금지 · ADR-0002/0005). 예약 생성 시 채팅방·예약 카드(BOOKING_CARD)·{@code BookingCreatedEvent}
 * 발행(chat 연동)은 후속·이연이다.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Booking",
    allowedDependencies = {"common", "listing :: api", "user :: api"})
package com.kohere.booking;
