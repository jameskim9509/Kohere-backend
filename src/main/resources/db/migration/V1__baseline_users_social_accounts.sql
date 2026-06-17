-- V1 baseline — auth·user MySQL 스키마 (ADR-0008, database-design §4-1·4-2).
-- 대상: users(user 모듈) · social_accounts(auth 모듈). refresh 토큰은 Redis(ADR-0006)라 제외.
-- community 테이블은 해당 JPA 엔티티 구현 시 후속 버전(V2~)으로 추가한다.
--
-- 코드 정합 주의:
--  - PII 컬럼(first_name·last_name·gender·birth_date·country_code·phone_number·visa_type)은 NULL 허용.
--    PENDING 회원은 온보딩 전이라 비어 있고, 탈퇴 시 즉시 익명화로 NULL이 된다(ADR-0014).
--    (database-design §4-2가 NOT NULL로 적은 것은 ACTIVE 상태 한정 의도 — 컬럼 제약으로는 NULL 허용이 맞다.)
--  - agreed_at(동의 시각)은 현재 도메인(User)·엔티티에 없어 제외한다. 코드에 도입되면 후속 마이그레이션으로 추가.

CREATE TABLE users (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    first_name              VARCHAR(100) NULL,
    last_name               VARCHAR(100) NULL,
    gender                  VARCHAR(16)  NULL,
    birth_date              DATE         NULL,
    country_code            VARCHAR(8)   NULL,
    phone_number            VARCHAR(20)  NULL,
    visa_type               VARCHAR(32)  NULL,
    status                  VARCHAR(16)  NOT NULL,
    terms_of_service_agreed BIT          NOT NULL,
    privacy_policy_agreed   BIT          NOT NULL,
    marketing_agreed        BIT          NOT NULL,
    terms_version           VARCHAR(20)  NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    withdrawn_at            DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE social_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NULL,
    linked_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_social_accounts_provider_user UNIQUE (provider, provider_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 회원 연동 목록·탈퇴 정리(deleteByUserId) 조회용 (database-design §4-1 INDEX user_id)
CREATE INDEX idx_social_accounts_user_id ON social_accounts (user_id);
