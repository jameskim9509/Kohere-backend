-- V14 — 예약 내역 삭제(참여자별 소프트삭제) 컬럼 추가 (#169).
-- bookings는 tenant_id·landlord_id가 공유하는 1행이라, 단일 삭제 플래그를 두면 한쪽이 지울 때 상대 기록까지 사라진다.
-- 그래서 참여자별로 2컬럼(NULL=미삭제)을 둔다 — 요청자 쪽 컬럼만 세팅하고, 조회 시 요청자 쪽 컬럼이 NULL인 예약만 노출한다.
-- bookings는 신규 테이블이라 기존 행이 없다(비어 있음 전제 · nullable 즉시 추가).
-- docs/api/specs/04-booking-inquiry-chat.md §4 · docs/database/database-design.md §4-5.
ALTER TABLE bookings ADD COLUMN tenant_deleted_at   DATETIME(6) NULL;
ALTER TABLE bookings ADD COLUMN landlord_deleted_at DATETIME(6) NULL;
