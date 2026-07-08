-- bookings에 소유자(임대인) 식별자 landlord_id를 추가한다. 임대인 받은 신청 조회(US-4-6, userType 분기)의 소유권 스코프 키.
-- 예약 생성 시 listing.landlordId 스냅샷을 저장하고, 임대인 조회는 landlord_id로 직접 스코핑한다(cross-store 조인 없음 · ADR-0005).
-- landlord_id는 user(값 참조, FK 없음). bookings는 신규 테이블이라 기존 행이 없다(비어 있음 전제 · NOT NULL 즉시 추가).
-- docs/database/database-design.md §4-5 · docs/api/specs/04-booking-inquiry-chat.md §2·§3.
ALTER TABLE bookings ADD COLUMN landlord_id BIGINT NOT NULL;

CREATE INDEX idx_bookings_landlord_created ON bookings (landlord_id, created_at);
