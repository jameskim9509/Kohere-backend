-- V7 — Apple refresh token 컬럼 추가 (ADR-0031, database-design §4-1).
-- Apple authorization code 교환(/auth/token)으로 받은 refresh token을 보관해 탈퇴 시 /auth/revoke로 폐기한다.
-- Apple 전용 — Google·교환 이전 행은 NULL. 1급 시크릿(로그·응답 미노출), MVP는 평문 + RDS 저장소 암호화(ADR-0015).
ALTER TABLE social_accounts
    ADD COLUMN apple_refresh_token VARCHAR(512) NULL AFTER email;
