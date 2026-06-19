-- V4 — 국가 참조 테이블(countries). 국적은 코드로 식별, 표시명·국기 resolve용 단일 출처 (database-design §4-2).
-- flag는 국기 이미지 URL(flagcdn.com SVG, 코드 소문자 기반). 대표 시드(한국 거주 외국인 다수 국적 위주).
-- 전체 ISO 3166-1 시드·다국어 표시명은 후속(문서 §6 확인 필요).
CREATE TABLE countries (
    code VARCHAR(2)   NOT NULL,
    name VARCHAR(64)  NOT NULL,
    flag VARCHAR(512) NOT NULL,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO countries (code, name, flag) VALUES
    ('KR', 'South Korea', 'https://flagcdn.com/kr.svg'),
    ('CN', 'China', 'https://flagcdn.com/cn.svg'),
    ('VN', 'Vietnam', 'https://flagcdn.com/vn.svg'),
    ('US', 'United States', 'https://flagcdn.com/us.svg'),
    ('UZ', 'Uzbekistan', 'https://flagcdn.com/uz.svg'),
    ('TH', 'Thailand', 'https://flagcdn.com/th.svg'),
    ('PH', 'Philippines', 'https://flagcdn.com/ph.svg'),
    ('NP', 'Nepal', 'https://flagcdn.com/np.svg'),
    ('MN', 'Mongolia', 'https://flagcdn.com/mn.svg'),
    ('JP', 'Japan', 'https://flagcdn.com/jp.svg'),
    ('ID', 'Indonesia', 'https://flagcdn.com/id.svg'),
    ('KH', 'Cambodia', 'https://flagcdn.com/kh.svg'),
    ('RU', 'Russia', 'https://flagcdn.com/ru.svg'),
    ('IN', 'India', 'https://flagcdn.com/in.svg'),
    ('MM', 'Myanmar', 'https://flagcdn.com/mm.svg');
