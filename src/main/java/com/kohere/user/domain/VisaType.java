package com.kohere.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 비자정보(온보딩 필수). 요구사항 정의서의 비자 드롭다운 확정 항목(#93, #138 개편).
 *
 * <p>API 요청·응답·저장 값은 enum 상수명이 아니라 {@link #getValue()}다 — 사람이 읽는 표시용 라벨 문자열이며 공백·괄호·구분기호를 포함한다(예:
 * {@code Short Term Visit(C-1~4, B)}, {@code Students & Trainees(D-2, D-3, D-4)}). 값이 UPPER_SNAKE가
 * 아니라 다른 enum 규약과 달리 예외적으로 표시 라벨을 그대로 값으로 쓴다(#138). docs/api/specs/01-auth-onboarding.md
 * (visaType).
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

  /** API·DB 노출 값(표시용 라벨, 예 {@code Short Term Visit(C-1~4, B)}). */
  @JsonValue
  public String getValue() {
    return value;
  }

  /** {@link #getValue()} 문자열로 enum을 역매핑한다(요청 역직렬화·DB 로드). 미정의 값은 예외. */
  @JsonCreator
  public static VisaType fromValue(String value) {
    for (VisaType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("정의되지 않은 VisaType 값입니다: " + value);
  }
}
