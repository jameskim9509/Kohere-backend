package com.kohere.user.domain;

/**
 * 직업 유형(온보딩 필수). <b>임시 분류값</b> — 요구사항 정의서의 직업 드롭다운 항목이 미확정(잘림)이라 잠정 정의이며 실제 선택지 확정 시 갱신한다(확인 필요).
 * docs/architecture/domain-model.md §2 · database-design §6.
 */
public enum Occupation {
  STUDENT,
  EMPLOYEE,
  SELF_EMPLOYED,
  JOB_SEEKER,
  ETC
}
