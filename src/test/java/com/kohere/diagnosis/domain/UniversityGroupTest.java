package com.kohere.diagnosis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 진단 ③ 대학 그룹 enum 단위 테스트(ADR-0028·ADR-0045). 그룹→멤버 확장과 {@code ETC} 여집합 매칭의 전제를 고정한다 — 둘 다 추천 결과를 직접
 * 바꾸는데, 어겨도 예외가 아니라 <b>조용히 틀린 결과</b>로 나타나기 때문이다.
 */
class UniversityGroupTest {

  @Test
  @DisplayName("ETC는 멤버를 갖지 않는다 — 대학의 묶음이 아니라 '위에 없음'이라는 답이다")
  void etcHasNoMembers() {
    // ETC에 코드를 넣으면 그 대학이 selectableMemberCodes()에 섞여 제외 집합에 들어가고,
    // 정확히 그 대학을 찾던 사용자에게서 매물이 사라진다. 넣는 순간 여기서 잡는다.
    assertThat(UniversityGroup.ETC.memberCodes()).isEmpty();
  }

  @Test
  @DisplayName("selectableMemberCodes는 ETC를 뺀 모든 그룹의 멤버 합집합이다")
  void selectableMemberCodesUnionsEveryGroupExceptEtc() {
    assertThat(UniversityGroup.selectableMemberCodes())
        .containsExactlyInAnyOrder(
            "HUFS",
            "KHU",
            "KOREA",
            "SKKU",
            "SUNGSHIN",
            "SNU",
            "CAU",
            "SOONGSIL",
            "HONGIK",
            "YONSEI",
            "EWHA",
            "KONKUK",
            "SEJONG",
            "HYU");
  }

  @Test
  @DisplayName("한 대학은 한 그룹에만 속한다 — 그룹 선택이 겹치면 추천이 중복 매칭된다")
  void memberCodesDoNotOverlapBetweenGroups() {
    List<String> allMembers =
        Arrays.stream(UniversityGroup.values())
            .flatMap(group -> group.memberCodes().stream())
            .toList();

    assertThat(allMembers).doesNotHaveDuplicates();
  }
}
