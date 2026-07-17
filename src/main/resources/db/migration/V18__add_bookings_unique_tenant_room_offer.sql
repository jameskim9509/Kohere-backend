-- V18 — 동일 세입자–동일 방 상품 예약(신청) 중복 방지 UNIQUE 추가 (#169).
-- V9의 "중복 방지 유니크 제약을 두지 않는다(같은 방 상품에 다건 신청 허용)" 결정을 되돌린다(V9 파일 자체는 배포돼 수정하지 않는다).
-- 상태 전이(수락/거절/취소)가 미구현이라 모든 예약이 REQUESTED(=활성)이므로 "활성 1건"이 곧 "전체 1건" — 조건 없는 UNIQUE로 규칙이 정확히 표현된다.
--   ⚠️ 상태 전이 도입 시 REJECTED·CANCELED 건이 같은 방 재신청을 영구 차단하므로 활성만 거르는 부분 유니크로 교체해야 한다
--      (MySQL은 부분 유니크 인덱스 미지원 — active_room_offer_id nullable 컬럼 + UNIQUE 트릭·앱 레벨 검사 등 표현 방식은 그때 정한다).
-- migration-policy §3상 제약 강화 = 비호환이라 기존 중복 행 정리가 선행돼야 하나 bookings는 신규라 사실상 비어 있다.
-- 삭제·차단·신고·사유 카탈로그용 V14~V17과 별개의 제약 변경이라 V18로 둔다.
-- 재신청 시 409 BOOKING_ALREADY_EXISTS(선언만 있던 코드를 실사용으로 전환). docs/database/database-design.md §2-4·§4-5 · docs/api/specs/04-booking-inquiry-chat.md §1.
ALTER TABLE bookings ADD CONSTRAINT uq_bookings_tenant_room_offer UNIQUE (tenant_id, room_offer_id);
