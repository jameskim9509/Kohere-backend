-- V16 — 예약 신고 접수 테이블 booking_reports 신설 (#169).
-- 신고는 예약 도메인의 접수(capture)까지만 담당한다 — 운영자 검토·제재·상태 전이가 범위 밖이라 status 컬럼이 없다(불변 기록).
-- reason은 선택(nullable, enum BookingReportReason). reporter_id는 user 값 참조(FK 없음), booking_id는 같은 모듈이나 레포 관행상 FK 미사용.
-- 동일 신고자·동일 예약 중복 신고는 유니크 (reporter_id, booking_id)로 막는다(409 BOOKING_REPORT_ALREADY_EXISTS).
-- 이 reports는 report 모듈의 콘텐츠 신고(reports 테이블 · 게시글·댓글·메시지)와 대상이 겹치지 않는 별개 테이블이다.
-- docs/database/database-design.md §4-5 · docs/architecture/domain-model.md §5 BookingReport.
CREATE TABLE booking_reports (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT      NOT NULL,
    booking_id  BIGINT      NOT NULL,
    reason      VARCHAR(32) NULL,
    detail      TEXT        NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_booking_reports_reporter_booking UNIQUE (reporter_id, booking_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
