-- V17 — 예약 신고 사유 카탈로그 테이블 booking_report_reasons 신설 + 초기 6종(×3언어) 시드 (#169).
-- 사유는 코드 배포 없이 행 INSERT로 동적 추가할 수 있어야 하므로 enum·리소스 번들이 아니라 DB 카탈로그로 둔다.
-- 언어 추가도 컬럼(ALTER)이 아니라 행(INSERT)으로 하도록 lang을 컬럼으로 둔다 — (code, lang) 한 쌍이 한 라벨이다.
-- code는 언어 무관 불변 식별자, 서버는 사용자 표시 언어(user::api getLanguage)의 라벨을 골라 내려준다(없으면 en 폴백).
-- booking_reports.reason은 이 카탈로그의 code 값을 값 참조로 저장한다(FK 없음 · 레포 관행).
-- docs/database/database-design.md §4-5 · docs/architecture/domain-model.md §5.
CREATE TABLE booking_report_reasons (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(32)  NOT NULL,
    lang          VARCHAR(8)   NOT NULL,
    label         VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL,
    active        BIT          NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    CONSTRAINT uq_booking_report_reasons_code_lang UNIQUE (code, lang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_booking_report_reasons_lang_active_order
    ON booking_report_reasons (lang, active, display_order);

INSERT INTO booking_report_reasons (code, lang, label, display_order, active) VALUES
    ('SPAM', 'en', 'Spam/Advertising', 1, b'1'),
    ('SPAM', 'ko', '스팸/광고', 1, b'1'),
    ('SPAM', 'ja', 'スパム/広告', 1, b'1'),
    ('ABUSE', 'en', 'Abuse/Harassment', 2, b'1'),
    ('ABUSE', 'ko', '욕설/괴롭힘', 2, b'1'),
    ('ABUSE', 'ja', '暴言/嫌がらせ', 2, b'1'),
    ('SEXUAL_CONTENT', 'en', 'Sexual content', 3, b'1'),
    ('SEXUAL_CONTENT', 'ko', '성적 콘텐츠', 3, b'1'),
    ('SEXUAL_CONTENT', 'ja', '性的コンテンツ', 3, b'1'),
    ('EXTERNAL_CONTACT', 'en', 'Soliciting external contact', 4, b'1'),
    ('EXTERNAL_CONTACT', 'ko', '외부 연락처 유도', 4, b'1'),
    ('EXTERNAL_CONTACT', 'ja', '外部連絡先への誘導', 4, b'1'),
    ('FALSE_INFO', 'en', 'False information', 5, b'1'),
    ('FALSE_INFO', 'ko', '허위 정보', 5, b'1'),
    ('FALSE_INFO', 'ja', '虚偽情報', 5, b'1'),
    ('ETC', 'en', 'Other', 6, b'1'),
    ('ETC', 'ko', '기타', 6, b'1'),
    ('ETC', 'ja', 'その他', 6, b'1');
