package com.kohere.user.domain;

/**
 * 직업 유형(온보딩 필수). 요구사항 정의서의 직업 드롭다운 확정 항목(#93). docs/architecture/domain-model.md §2 ·
 * database-design §6.
 */
public enum Occupation {
  /** 학부생(undergraduate student). */
  UNDERGRADUATE_STUDENT,
  /** 대학원생(graduate student). */
  GRADUATE_STUDENT,
  /** 교환학생(exchange student). */
  EXCHANGE_STUDENT,
  /** 교육/학술 연구(education/academic research). */
  EDUCATION_ACADEMIC_RESEARCH,
  /** IT/소프트웨어 엔지니어링(it/software engineering). */
  IT_SOFTWARE_ENGINEERING,
  /** 개발자(developer). */
  DEVELOPER,
  /** 디자이너(designer). */
  DESIGNER
}
