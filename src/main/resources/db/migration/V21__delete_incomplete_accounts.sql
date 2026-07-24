-- #192: 이름·이메일 수집 시점이 소셜 로그인으로 이동해, 온보딩을 끝내지 못한(PENDING/TERMS_AGREED) 기존 계정은
-- 이름·이메일이 비어 재로그인 시 정합이 깨진다. 이런 미완료 계정과 그 소셜 매핑을 정리해 신규 흐름으로 재가입하게 한다.
DELETE FROM social_accounts
WHERE user_id IN (SELECT id FROM users WHERE status IN ('PENDING', 'TERMS_AGREED'));

DELETE FROM users WHERE status IN ('PENDING', 'TERMS_AGREED');
