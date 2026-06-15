package com.kohere.chat.application;

import com.kohere.booking.BookingCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * booking 모듈의 예약 생성 이벤트를 구독해 임대인과의 채팅방 보장(없으면 생성) + 예약 카드(BOOKING_CARD) 고정 전송 + 임대인 푸시 알림을 처리한다.
 * booking은 chat을 모르고(역의존 없음), chat이 이벤트만 단방향 구독한다(느슨한 결합, ADR-0002).
 *
 * <p>TODO: 영속/트랜잭션 도입 후 {@link EventListener}(동기) → {@code @ApplicationModuleListener}(비동기 + 커밋 이후
 * 트랜잭션 바운드)로 교체한다.
 */
@Slf4j
@Component
public class BookingEventHandler {

  @EventListener
  public void onBookingCreated(BookingCreatedEvent event) {
    // TODO: 채팅방 보장 + BOOKING_CARD 시스템 메시지 고정 전송 + 임대인 푸시 알림
    log.info("BookingCreatedEvent 수신: bookingId={}", event.bookingId());
  }
}
