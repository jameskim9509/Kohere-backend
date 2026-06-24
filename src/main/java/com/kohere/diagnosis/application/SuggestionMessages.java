package com.kohere.diagnosis.application;

import com.kohere.diagnosis.application.dto.RecommendationResponse.SuggestionAction;
import com.kohere.diagnosis.application.dto.RecommendationResponse.Suggestions;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 추천 0건 조정 제안(suggestions)의 번역 메시지 제공(diagnosis 보유 언어-키 reference, ADR-0029). {@code reason}/{@code
 * type}은 언어 무관 enum 키이고 사람이 보는 message/detail만 사용자 언어로 제공하며, 없으면 영어로 폴백한다.
 */
@Component
public class SuggestionMessages {

  private static final String REASON_NO_MATCH = "NO_MATCH";

  private static final Map<String, String> NO_MATCH_MESSAGE =
      Map.of(
          "en", "No listings matched your criteria. Try adjusting the options below.",
          "ko", "조건에 맞는 매물이 없습니다. 아래 항목을 조정해 보세요.");

  private static final List<Action> ACTIONS =
      List.of(
          new Action("RELAX_REGION", Map.of("en", "Widen the region.", "ko", "지역 범위를 넓혀 보세요.")),
          new Action(
              "RELAX_CONDITIONS",
              Map.of("en", "Reduce housing conditions.", "ko", "주거 조건을 줄여 보세요.")),
          new Action(
              "INCREASE_BUDGET",
              Map.of("en", "Increase the monthly budget.", "ko", "월 예산을 높여 보세요.")));

  /** 매칭 0건 제안을 사용자 언어로 구성한다. */
  public Suggestions noMatch(String language) {
    String message = pick(NO_MATCH_MESSAGE, language);
    List<SuggestionAction> actions =
        ACTIONS.stream()
            .map(a -> new SuggestionAction(a.type(), pick(a.detail(), language)))
            .toList();
    return new Suggestions(REASON_NO_MATCH, message, actions);
  }

  private static String pick(Map<String, String> map, String language) {
    return map.getOrDefault(language, map.get(CountryLanguageResolver.DEFAULT_LANGUAGE));
  }

  private record Action(String type, Map<String, String> detail) {}
}
