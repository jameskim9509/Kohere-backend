-- V10 — 온보딩 직업(Occupation)·비자(VisaType) 확정 드롭다운 값 반영에 따른 기존 데이터 이행 (#93).
-- visa_type은 값에 체류자격 코드를 이어붙인 형식(예: STUDY_D-2, 최장 PROFESSIONAL_C-4_..._E-7 61자)이라
-- 컬럼을 VARCHAR(32)→VARCHAR(80)으로 넓힌다. occupation은 코드 없이 최장 27자라 DDL 변경 없음.
--
-- 이행 원칙: 의미가 확실히 대응되는 구값만 매핑하고, 근거 없는 추정 매핑은 하지 않는다.
--  - visa_type: VISA_STUDENT→STUDY_D-2(유학), VISA_TOURISM→SHORT_TERM_VISIT_C-2_C-3(단기방문),
--    VISA_WORKING_HOLIDAY→WORKING_HOLIDAY_H-1(워킹홀리데이).
--  - 나머지 구값(occupation 전체, VISA_WORK·VISA_RESIDENCE·VISA_ETC)은 새 분류에
--    대응 항목이 없어 NULL 처리한다. 두 컬럼은 NULL 허용(PENDING 미입력·탈퇴 익명화와 동일 의미, V1·V3 참조)이며
--    사용자는 프로필 수정(PATCH /api/v1/users/me)으로 재입력할 수 있다.
--    (구 STUDENT는 학부/대학원/교환학생 3분화로 어느 항목인지 알 수 없어 매핑하지 않는다.)

ALTER TABLE users MODIFY visa_type VARCHAR(80) NULL;

UPDATE users SET visa_type = 'STUDY_D-2' WHERE visa_type = 'VISA_STUDENT';
UPDATE users SET visa_type = 'SHORT_TERM_VISIT_C-2_C-3' WHERE visa_type = 'VISA_TOURISM';
UPDATE users SET visa_type = 'WORKING_HOLIDAY_H-1' WHERE visa_type = 'VISA_WORKING_HOLIDAY';
UPDATE users SET visa_type = NULL
WHERE visa_type IN ('VISA_WORK', 'VISA_RESIDENCE', 'VISA_ETC');

UPDATE users SET occupation = NULL
WHERE occupation IN ('STUDENT', 'EMPLOYEE', 'SELF_EMPLOYED', 'JOB_SEEKER', 'ETC');
