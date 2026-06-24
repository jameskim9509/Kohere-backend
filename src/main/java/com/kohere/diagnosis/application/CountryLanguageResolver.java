package com.kohere.diagnosis.application;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 등록 국가(ISO 코드)→표시 언어 매핑(diagnosis 보유 reference, ADR-0029). 매핑이 없거나 국가가 비어 있으면 영어(en)로 폴백한다(에러 아님).
 * 실제 라벨이 해당 언어로 없으면 카탈로그 조립 단계에서 다시 en으로 폴백한다.
 */
@Component
public class CountryLanguageResolver {

  /** 기본·폴백 언어. */
  public static final String DEFAULT_LANGUAGE = "en";

  private static final Map<String, String> COUNTRY_TO_LANGUAGE =
      Map.ofEntries(
          Map.entry("KR", "ko"),
          Map.entry("JP", "ja"),
          Map.entry("CN", "zh"),
          Map.entry("TW", "zh"),
          Map.entry("HK", "zh"),
          Map.entry("VN", "vi"),
          Map.entry("US", "en"),
          Map.entry("GB", "en"));

  /** 등록 국가 코드로 표시 언어를 결정한다(미매핑·null이면 en). */
  public String resolve(String country) {
    if (country == null || country.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    return COUNTRY_TO_LANGUAGE.getOrDefault(country.toUpperCase(), DEFAULT_LANGUAGE);
  }
}
