-- V8 — 임대인 회원 컬럼 추가 (ADR-0033·ADR-0034, database-design §4-2).
-- user_type(역할 — 온보딩 제출로 확정·이후 불변), phone_number(임대인 연락처 — SMS 인증값, 세입자 NULL),
-- business_registration_number_hash(사업자등록번호 SHA-256 해시 — 원문 비저장·로그 비저장·응답 마스킹, 세입자 NULL).
-- 기존 행은 user_type DEFAULT 'TENANT'로 백필(Hibernate validate는 VARCHAR 타입만 검사 — 길이/NOT NULL 비검사).
ALTER TABLE users
    ADD COLUMN user_type VARCHAR(16) NOT NULL DEFAULT 'TENANT',
    ADD COLUMN phone_number VARCHAR(20) NULL,
    ADD COLUMN business_registration_number_hash VARCHAR(64) NULL;

-- 역할별 조회·집계용 인덱스(database-design §4-2).
CREATE INDEX idx_users_user_type ON users (user_type);
