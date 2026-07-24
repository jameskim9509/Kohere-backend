-- #192: provider가 준 표시 이름 스냅샷을 social_accounts에 보관한다(User.name과 별개, 로그인마다 갱신).
ALTER TABLE social_accounts ADD COLUMN name VARCHAR(200) NULL;
