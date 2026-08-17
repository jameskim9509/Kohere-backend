-- V22 — 임대인 웹 로컬 자격증명 테이블 local_accounts 신설 (#229, ADR-0047).
-- 웹 로그인(이메일+비밀번호)의 자격증명은 auth가 소유한다. users(user 모듈)에 password_hash·failed_login_attempts·
-- locked_at을 붙이면 auth가 user의 테이블을 쓰는 구조가 되어 모듈 경계가 뚫린다(ADR-0001). 소셜 자격증명이 users가 아니라
-- social_accounts에 따로 있는 것과 같은 모양이며, 부수 효과로 세입자 다수의 users 행에 영원히 NULL인 컬럼 넷이 생기지 않는다.
-- uq_local_accounts_email  — 웹 로그인 ID 유일성. 중복을 허용하면 로그인이 계정을 특정할 수 없다(US-1-12).
-- uq_local_accounts_user_id — "한 계정에 웹 자격증명 하나"를 DB 레벨로 강제하는 제약이다. 번호로 찾은 기존 계정에 자격증명을
--   덧붙이는 연동 경로(US-1-11)는 조회 후 INSERT라 동시 요청에 check-then-act가 깨지는데, 두 번째 INSERT가 여기서 막힌다
--   (선검사에서 걸리는 경우가 409 AUTH_WEB_ACCOUNT_ALREADY_EXISTS다).
-- user_id에 FK는 걸지 않는다 — social_accounts가 V1에서 의도적으로 생략한 선례를 따른다(값 참조, ADR-0002/0005).
--   uq_local_accounts_user_id가 user_id 인덱스를 겸하므로 별도 보조 인덱스도 두지 않는다.
-- name·birth_date는 가입 폼 스냅샷이라 NULL 허용이다 — 연동 시 users의 프로필을 갱신하지 않으므로 두 값이 다를 수 있다.
-- password_hash VARCHAR(100) — BCrypt 60자 + 여유. 원문은 저장·로그 어디에도 남기지 않는다.
-- 실패 카운터·잠금 시각을 Redis TTL이 아니라 컬럼으로 두는 이유는, TTL이 만료되면 잠금이 저절로 풀려
-- "해제 기능 없음"이라는 정책(US-1-12)이 깨지기 때문이다.
-- (Hibernate validate는 VARCHAR 타입만 검사 — 길이·NOT NULL·UNIQUE는 이 DDL이 정본이다.)
CREATE TABLE local_accounts (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    name                  VARCHAR(200) NULL,
    birth_date            DATE         NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_at             DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_local_accounts_email   UNIQUE (email),
    CONSTRAINT uq_local_accounts_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
