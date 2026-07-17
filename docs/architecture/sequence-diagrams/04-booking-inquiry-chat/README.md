# 시퀀스 다이어그램 — 매물 예약(신청) · (후속) 문의·인앱 채팅

> 사용자 → 앱(클라이언트) → 백엔드(서버) 흐름. 관련: [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/04-booking-inquiry-chat.md)

## [1차 MVP] 매물 예약(신청)

인앱 채팅과 분리된 독립 기능. 예약을 저장하고(US-4-1), 예약 조회 엔드포인트는 요청자 `userType`으로 분기해 세입자는 내 예약을(US-4-2), 임대인은 자기 소유 매물에 신청된 예약을(US-4-6) 목록·상세로 조회한다. US-4-2·US-4-6은 **같은 엔드포인트**(`GET /api/v1/bookings`·`/{bookingId}`)의 두 `userType` 분기다. 여기에 예약 내역의 삭제(US-4-7)·상대 차단(US-4-8)·신고 접수(US-4-9)가 붙는다 — 삭제·차단은 **조회 경로의 필터**(US-4-2·US-4-6)로만 관측되고, 신고는 그 필터를 타지 않는다.

| 스토리 | 제목 | 다이어그램 |
| --- | --- | --- |
| US-4-1 | 매물 예약 생성(신청 저장) | [us-4-1-booking-create](us-4-1-booking-create.md) |
| US-4-2 | 내 예약 조회(목록·단건 상세, 세입자 분기) | [us-4-2-booking-retrieve](us-4-2-booking-retrieve.md) |
| US-4-6 | 임대인 받은 신청 조회(내 매물, 임대인 분기) | [us-4-6-landlord-booking-retrieve](us-4-6-landlord-booking-retrieve.md) |
| US-4-7 | 예약 내역 삭제(참여자별 숨김) | [us-4-7-booking-delete](us-4-7-booking-delete.md) |
| US-4-8 | 예약 상대 차단(사용자 단위) | [us-4-8-booking-block](us-4-8-booking-block.md) |
| US-4-9 | 예약 신고(접수) | [us-4-9-booking-report](us-4-9-booking-report.md) |

## [후속·이연] 문의 · 인앱 채팅

인앱 채팅(기존 F-03 chat 결합)으로 1차 MVP에서 후속으로 분리·이연한다. 예약 생성 시 채팅방에 `BOOKING_CARD`를 자동 기록하던 결합도 함께 이연한다. 파일명은 legacy(`us-4-2`~`us-4-4`)를 유지하되, 대응 US 번호는 각각 **US-4-3 / US-4-4 / US-4-5** 로 재정합됐다.

| 스토리 | 제목 | 다이어그램 |
| --- | --- | --- |
| US-4-3 | 매물 문의(채팅방 생성/조회) 및 매물 카드 고정 | [us-4-2-inquiry-chatroom](us-4-2-inquiry-chatroom.md) |
| US-4-4 | 채팅방 리스트 조회 | [us-4-3-chatroom-list](us-4-3-chatroom-list.md) |
| US-4-5 | 채팅 메시지 조회·전송·읽음 처리 | [us-4-4-chat-messages](us-4-4-chat-messages.md) |
