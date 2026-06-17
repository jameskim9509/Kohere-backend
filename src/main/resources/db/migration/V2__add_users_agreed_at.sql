-- V2 — Consent 동의 시각(agreed_at) 추가 (domain-model Consent.agreedAt, database-design §4-2).
-- 온보딩 완료 시 기록(User.completeOnboarding), 탈퇴 시에도 보존한다 —
-- 식별 PII가 아닌 동의 증빙이라 termsVersion과 동일 취급(ADR-0014). PENDING 회원은 미동의라 NULL.
ALTER TABLE users
    ADD COLUMN agreed_at DATETIME(6) NULL AFTER terms_version;
