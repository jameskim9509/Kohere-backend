-- V15 — 사용자 간 차단 테이블 user_blocks 신설 (#169).
-- 차단은 사용자 단위(전역)다 — 한 임대인이 매물(listing)을, 한 매물이 방 상품(room_offer)을 여러 개 가지므로
-- 예약/방 단위 차단은 상대의 다른 방으로 우회된다. 차단 대상은 예약이 아니라 사람이다.
-- 행 존재 = 차단, 해제 = 행 삭제(is_active 없음). blocker_id·blocked_user_id는 user 값 참조(FK 없음 · 레포 관행).
-- 목록·해제는 user 모듈(/api/v1/users/me/blocks)이 제공하고, 생성은 booking이 예약에서 상대를 도출해 user::api 공개 명령으로 위임한다.
-- docs/database/database-design.md §4-2 · docs/architecture/domain-model.md §2 UserBlock.
CREATE TABLE user_blocks (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id      BIGINT      NOT NULL,
    blocked_user_id BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_blocks_blocker_blocked UNIQUE (blocker_id, blocked_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_user_id);
