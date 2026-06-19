-- V5 — 닉네임 단어 풀(형용사·사물). NicknameGenerator가 active 단어에서 무작위로 조합(형용사+사물) + 유니크 충돌 재조합 (database-design §4-2).
-- 대표 시드(확장·로케일·조합 포맷은 후속, 문서 §6 확인 필요). active=0으로 비속어 등 사후 비활성 가능.
CREATE TABLE nickname_adjectives (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    word   VARCHAR(50) NOT NULL,
    active BIT         NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uq_nickname_adjectives_word UNIQUE (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE nickname_nouns (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    word   VARCHAR(50) NOT NULL,
    active BIT         NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uq_nickname_nouns_word UNIQUE (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO nickname_adjectives (word) VALUES
    ('Brave'), ('Calm'), ('Bright'), ('Swift'), ('Gentle'), ('Bold'), ('Lucky'), ('Witty'),
    ('Cosmic'), ('Sunny'), ('Clever'), ('Mighty'), ('Quiet'), ('Happy'), ('Curious'), ('Noble'),
    ('Shiny'), ('Wild'), ('Cozy'), ('Lively');

INSERT INTO nickname_nouns (word) VALUES
    ('Otter'), ('Falcon'), ('Maple'), ('Tiger'), ('Panda'), ('Comet'), ('River'), ('Fox'),
    ('Whale'), ('Sparrow'), ('Lotus'), ('Pixel'), ('Ember'), ('Cloud'), ('Acorn'), ('Dolphin'),
    ('Willow'), ('Harbor'), ('Meadow'), ('Pebble');
