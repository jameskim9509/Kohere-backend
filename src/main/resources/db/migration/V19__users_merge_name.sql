-- #192: 세입자의 first_name/last_name를 임대인과 통일된 단일 name 컬럼으로 통합한다.
-- 기존 데이터는 두 컬럼을 공백으로 이어붙여 옮기되, 둘 다 비면 NULL로 둔다.
ALTER TABLE users ADD COLUMN name VARCHAR(200) NULL AFTER last_name;

UPDATE users SET name = NULLIF(TRIM(CONCAT_WS(' ', first_name, last_name)), '');

ALTER TABLE users DROP COLUMN first_name;
ALTER TABLE users DROP COLUMN last_name;
