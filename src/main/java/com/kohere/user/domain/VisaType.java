package com.kohere.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 비자정보(온보딩 필수). 요구사항 정의서의 비자 드롭다운 확정 항목(#93).
 *
 * <p>API 요청·응답·저장 값은 enum 상수명이 아니라 {@link #getValue()}다 — 카테고리명 뒤에 출입국 체류자격 코드를 언더스코어로 이어붙인 형식(예:
 * {@code STUDY_D-2}, {@code DIPLOMATIC_OFFICIAL_A-1_A-2}). 코드에 하이픈이 들어가 다른 enum의 UPPER_SNAKE 규약과 달리
 * 예외적으로 하이픈을 허용한다(#93). docs/api/specs/01-auth-onboarding.md (visaType).
 */
public enum VisaType {
  /** 외교·공무. */
  DIPLOMATIC_OFFICIAL("DIPLOMATIC_OFFICIAL_A-1_A-2"),
  /** 사증면제. */
  VISA_EXEMPTED("VISA_EXEMPTED_B"),
  /** 취재·종교. */
  JOURNALISM_RELIGIOUS_AFFAIRS("JOURNALISM_RELIGIOUS_AFFAIRS_C-1_D-5_D-6"),
  /** 단기방문. */
  SHORT_TERM_VISIT("SHORT_TERM_VISIT_C-2_C-3"),
  /** 유학. */
  STUDY("STUDY_D-2"),
  /** 연수. */
  TRAINEE("TRAINEE_D-3_D-4"),
  /** 주재. */
  INTRA_COMPANY_TRANSFER("INTRA_COMPANY_TRANSFER_D-7"),
  /** 전문인력. */
  PROFESSIONAL("PROFESSIONAL_C-4_D-1_D-8_D-9_D-10_E-1_E-2_E-3_E-4_E-5_E-6_E-7"),
  /** 비전문취업. */
  NON_PROFESSIONAL("NON_PROFESSIONAL_E-8_E-9_E-10"),
  /** 워킹홀리데이. */
  WORKING_HOLIDAY("WORKING_HOLIDAY_H-1"),
  /** 방문취업. */
  WORK_AND_VISIT("WORK_AND_VISIT_H-2"),
  /** 방문동거·거주·동반. */
  FAMILY_VISITOR_DEPENDENT("FAMILY_VISITOR_DEPENDENT_F-1_F-2_F-3"),
  /** 재외동포. */
  OVERSEAS_KOREAN("OVERSEAS_KOREAN_F-4"),
  /** 영주. */
  PERMANENT_RESIDENCE("PERMANENT_RESIDENCE_F-5"),
  /** 결혼이민. */
  MARRIAGE_MIGRANT("MARRIAGE_MIGRANT_F-6"),
  /** 기타. */
  OTHERS("OTHERS_G-1");

  private final String value;

  VisaType(String value) {
    this.value = value;
  }

  /** API·DB 노출 값(카테고리 + 체류자격 코드, 예 {@code STUDY_D-2}). */
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
