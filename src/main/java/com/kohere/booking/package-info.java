/**
 * 매물 신청(예약) Bounded Context. 세입자가 매물에 입주 희망일·계약기간으로 신청(예약)을 생성하는 것을 담당한다. 동일 세입자–매물의 활성 예약은 1건만
 * 허용한다(중복 신청 방지).
 *
 * <p>도메인 에러 코드 prefix: {@code BOOKING}. 스펙: docs/api/specs/04-booking-inquiry-chat.md (신청/예약 부분).
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다.
 *
 * <p>TODO: 예약 생성 시 임대인과의 채팅방·예약 카드(BOOKING_CARD) 생성·고정은 chat 모듈의 책임이다. 현재는 chat 모듈을 직접 의존하지 않으며, 추후
 * 공개 API 또는 도메인 이벤트(BookingCreated)로 연결한다(chat 타입 import 금지).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Booking",
    allowedDependencies = {"common"})
package com.kohere.booking;
