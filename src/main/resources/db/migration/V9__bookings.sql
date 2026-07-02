-- 매물 예약(신청) 테이블. docs/api/specs/04-booking-inquiry-chat.md · docs/database/database-design.md §4-5.
-- MVP의 예약은 "신청" 성격이라 중복 방지 유니크 제약을 두지 않는다(같은 방 상품에 다건 신청 허용).
-- listing_id·room_offer_id는 MongoDB ObjectId 문자열 값 참조(FK 없음 · cross-store · ADR-0005).
CREATE TABLE bookings (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT       NOT NULL,
    listing_id      VARCHAR(24)  NOT NULL,
    room_offer_id   VARCHAR(24)  NOT NULL,
    move_in_date    DATE         NOT NULL,
    contract_period INT          NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_bookings_tenant_created ON bookings (tenant_id, created_at);
