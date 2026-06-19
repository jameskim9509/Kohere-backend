-- V3 — users 재구성: 전화번호 제거, 국적(코드)·직업·이메일·닉네임 추가 (database-design §4-2, 시퀀스 변경 반영).
-- PII 컬럼은 NULL 허용(PENDING은 미입력, 탈퇴 시 익명화로 NULL). nickname은 전역 UNIQUE(NULL 다중 허용이라 온보딩 전 PENDING 다건 무방).
-- 상태에 TERMS_AGREED가 추가되나 status는 VARCHAR(enum 문자열)이라 DDL 변경 없음.
ALTER TABLE users
    DROP COLUMN country_code,
    DROP COLUMN phone_number,
    ADD COLUMN nickname   VARCHAR(50)  NULL AFTER last_name,
    ADD COLUMN country    VARCHAR(2)   NULL AFTER birth_date,
    ADD COLUMN occupation VARCHAR(32)  NULL AFTER country,
    ADD COLUMN email      VARCHAR(255) NULL AFTER occupation,
    ADD CONSTRAINT uq_users_nickname UNIQUE (nickname);
