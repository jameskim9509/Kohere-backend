/**
 * 채팅 Bounded Context. 매물 임대인과의 1:1 채팅방(ChatRoom)과 메시지(Message)를 담당한다. 문의(inquiry)는 매물 임대인과의 채팅방을
 * 생성/반환하고, 채팅방 리스트·메시지 조회(커서)·텍스트 메시지 전송·읽음 처리를 제공한다. 커뮤니티의 이웃(NEIGHBOR) 채팅도 이 모듈을 재사용한다.
 *
 * <p>도메인 에러 코드 prefix: {@code CHAT}. 스펙: docs/api/specs/04-booking-inquiry-chat.md(문의/채팅 부분).
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에 의존하고, 모듈 간 통신은 도메인
 * 이벤트 기반이다(ADR-0002). booking 모듈이 발행하는 {@code BookingCreatedEvent}를 구독하므로 {@code booking}을 허용 의존에
 * 둔다(이벤트 타입에 대한 단방향 의존; booking은 chat을 모른다).
 *
 * <p>TODO: 문의 방 생성은 매물 소유자(listing 모듈) 정보가 필요하고, 이웃 채팅은 게시글(community 모듈)에서 시작된다 — 결과가 즉시 필요한 협력은
 * 공개 query API로, 부수효과(알림 등)는 도메인 이벤트로 연결한다(다른 모듈 타입 직접 import 금지).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Chat",
    allowedDependencies = {"common", "booking"})
package com.kohere.chat;
