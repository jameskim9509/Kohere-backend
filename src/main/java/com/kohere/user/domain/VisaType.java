package com.kohere.user.domain;

/**
 * 비자정보(온보딩 필수). 요구사항 정의서의 비자 드롭다운 확정 항목(#93, #138 개편).
 *
 * <p>API(요청·응답)는 다른 enum과 동일하게 상수명(코드, 예 {@code SHORT_TERM_VISIT})으로 주고받는다. 다만 DB에는 상수명이 아니라 사람이 읽는
 * 표시용 라벨({@link #getValue()}, 예 {@code Short Term Visit(C-1~4, B)})을 저장한다 — 영속은 {@code
 * VisaTypeConverter}가 {@link #getValue()}/{@link #fromValue(String)}로 처리한다.
 * docs/api/specs/01-auth-onboarding.md (visaType).
 */
public enum VisaType {
  /** 단기방문(C-1~4, B). */
  SHORT_TERM_VISIT("Short Term Visit(C-1~4, B)"),
  /** 유학·연수(D-2, D-3, D-4). */
  STUDENTS_TRAINEES("Students & Trainees(D-2, D-3, D-4)"),
  /** 비전문취업(E-8, E-9, E-10, H-2). */
  NON_PROFESSIONAL_WORKERS("Non-Professional Workers(E-8, E-9, E-10, H-2)"),
  /** 워킹홀리데이·방문취업(H-1, H-2). */
  WORKING_HOLIDAY_WORK_AND_VISIT("Working Holiday/Work and Visit(H-1, H-2)"),
  /** 재외동포(F-4). */
  OVERSEAS_KOREANS("Overseas Koreans(F-4)"),
  /** 방문동거·거주·결혼이민(F-1, F-2, F-3, F-6). */
  FAMILY_MARRIAGE_MIGRANTS("Family/Marriage Migrants(F-1, F-2, F-3, F-6)"),
  /** 영주(F-5). */
  PERMANENT_RESIDENTS("Permanent Residents(F-5)"),
  /** 전문인력(C-4, D-1, D-7~10, E-1~7). */
  PROFESSIONALS("Professionals(C-4, D-1, D-7~10, E-1~7)"),
  /** 외교·공무·기타(A-1, A-2, G-1). */
  DIPLOMATIC_OFFICIAL_AND_OTHERS("Diplomatic/Official & Others(A-1, A-2, G-1)"),
  /** 기타. */
  ETC("etc");

  private final String value;

  VisaType(String value) {
    this.value = value;
  }

  /** DB 저장용 표시 라벨(예 {@code Short Term Visit(C-1~4, B)}). API 노출은 상수명이다. */
  public String getValue() {
    return value;
  }

  /** {@link #getValue()} 라벨로 enum을 역매핑한다(DB 로드). 미정의 값은 예외. */
  public static VisaType fromValue(String value) {
    for (VisaType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("정의되지 않은 VisaType 값입니다: " + value);
  }
}
