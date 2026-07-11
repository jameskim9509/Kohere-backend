-- V12 — 온보딩 직업(Occupation)·비자(VisaType) 분류값 개편(#138)에 따른 기존 데이터 이행.
-- V10(#93)이 확정한 값을 재개편한다. visa_type 값 형식이 '상수명_체류자격코드'(예: STUDY_D-2)에서
-- 표시용 라벨 문자열(예: 'Students & Trainees(D-2, D-3, D-4)')로 바뀐다. 최장 45자라 V10에서 넓힌
-- VARCHAR(80) 안에 들어가 DDL 변경은 없다. occupation은 상수명 저장(@Enumerated STRING)이라 신규
-- 값도 최장 24자(MANUFACTURING_PRODUCTION) < VARCHAR(32)로 DDL 변경 없음.
--
-- 이행 원칙: 체류자격 코드가 겹치는 구값은 대응 신 카테고리로 매핑하고, 새 분류에 대응 항목이 없는 구값은
-- ETC로 이행한다(#138). 두 컬럼은 NULL 허용(미입력·탈퇴 익명화와 동일 의미)이며 사용자는 프로필 수정
-- (PATCH /api/v1/users/me)으로 재입력할 수 있다.

-- VisaType (구 '상수명_코드' → 신 표시 라벨). 각 구값은 정확히 한 문장에서만 매칭되어 순서 무관.
UPDATE users SET visa_type = 'Diplomatic/Official & Others(A-1, A-2, G-1)'
WHERE visa_type IN ('DIPLOMATIC_OFFICIAL_A-1_A-2', 'OTHERS_G-1');
UPDATE users SET visa_type = 'Short Term Visit(C-1~4, B)'
WHERE visa_type IN ('VISA_EXEMPTED_B', 'SHORT_TERM_VISIT_C-2_C-3');
UPDATE users SET visa_type = 'Students & Trainees(D-2, D-3, D-4)'
WHERE visa_type IN ('STUDY_D-2', 'TRAINEE_D-3_D-4');
UPDATE users SET visa_type = 'Professionals(C-4, D-1, D-7~10, E-1~7)'
WHERE visa_type IN (
  'INTRA_COMPANY_TRANSFER_D-7',
  'PROFESSIONAL_C-4_D-1_D-8_D-9_D-10_E-1_E-2_E-3_E-4_E-5_E-6_E-7');
UPDATE users SET visa_type = 'Non-Professional Workers(E-8, E-9, E-10, H-2)'
WHERE visa_type = 'NON_PROFESSIONAL_E-8_E-9_E-10';
UPDATE users SET visa_type = 'Working Holiday/Work and Visit(H-1, H-2)'
WHERE visa_type IN ('WORKING_HOLIDAY_H-1', 'WORK_AND_VISIT_H-2');
UPDATE users SET visa_type = 'Family/Marriage Migrants(F-1, F-2, F-3, F-6)'
WHERE visa_type IN ('FAMILY_VISITOR_DEPENDENT_F-1_F-2_F-3', 'MARRIAGE_MIGRANT_F-6');
UPDATE users SET visa_type = 'Overseas Koreans(F-4)'
WHERE visa_type = 'OVERSEAS_KOREAN_F-4';
UPDATE users SET visa_type = 'Permanent Residents(F-5)'
WHERE visa_type = 'PERMANENT_RESIDENCE_F-5';
-- 새 분류에 대응 없는 구값 → ETC('etc')
UPDATE users SET visa_type = 'etc'
WHERE visa_type = 'JOURNALISM_RELIGIOUS_AFFAIRS_C-1_D-5_D-6';

-- Occupation (상수명 저장). UNDERGRADUATE/GRADUATE/EXCHANGE_STUDENT는 상수 유지라 이행 대상 아님.
-- 새 분류에 대응 없는 구값(교육/학술·IT·개발자·디자이너) → ETC.
UPDATE users SET occupation = 'ETC'
WHERE occupation IN (
  'EDUCATION_ACADEMIC_RESEARCH', 'IT_SOFTWARE_ENGINEERING', 'DEVELOPER', 'DESIGNER');
