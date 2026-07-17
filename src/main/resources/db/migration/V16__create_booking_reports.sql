-- V16 — 예약 신고 접수 테이블 booking_reports 신설 (#169).
-- 신고는 예약 도메인의 접수(capture)까지만 담당한다 — 운영자 검토·제재·상태 전이가 범위 밖이라 status 컬럼이 없다(불변 기록).
-- reason은 선택(nullable, 카탈로그 code 값 참조). reporter_id는 user 값 참조(FK 없음), booking_id는 같은 모듈이나 레포 관행상 FK 미사용.
-- 동일 신고자·동일 예약을 여러 번 신고할 수 있다(다건 허용) — 새 사유·지속 문제를 다시 신고할 수 있어야 하기 때문이다.
--   신고 도배 방지는 레이트리밋(429)으로 다루며, 이는 후속·이연이다(현재 미구현). 그래서 (reporter_id, booking_id) 유니크를 두지 않는다.
-- 이 reports는 report 모듈의 콘텐츠 신고(reports 테이블 · 게시글·댓글·메시지)와 대상이 겹치지 않는 별개 테이블이다.
-- docs/database/database-design.md §4-5 · docs/architecture/domain-model.md §5 BookingReport.
CREATE TABLE booking_reports (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT      NOT NULL,
    booking_id  BIGINT      NOT NULL,
    reason      VARCHAR(32) NULL,
    detail      TEXT        NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 신고 대상별 조회(운영 큐 도입 시)·신고자별 레이트리밋 집계용 보조 인덱스.
CREATE INDEX idx_booking_reports_booking ON booking_reports (booking_id);
CREATE INDEX idx_booking_reports_reporter_created ON booking_reports (reporter_id, created_at);
