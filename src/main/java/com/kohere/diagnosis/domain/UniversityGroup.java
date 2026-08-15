package com.kohere.diagnosis.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 진단 ③ 대학 그룹 선택(단일). 개별 대학 15개를 지리적으로 가까운 6개 그룹으로 묶은 enum이며, 입국 목적이 {@code STUDY}일 때만 필수다. 사용자는 그룹
 * 하나를 고르고, 추천 시 그룹은 소속 개별 대학 코드({@link #memberCodes()})로 펼쳐져 listing의 {@code
 * nearbyUniversityCodes}와 ANY 매칭된다(그룹→멤버 확장은 diagnosis 책임). 멤버 코드는 listing이 저장하는 개별 대학 코드 문자열과 동일해야
 * 한다.
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md(③ 대학·지역 선택) · ADR-0028.
 */
public enum UniversityGroup {
  HUFS_KHU_KOREA(Set.of("HUFS", "KHU", "KOREA")),
  SKKU_SUNGSHIN(Set.of("SKKU", "SUNGSHIN")),
  SNU_CAU_SOONGSIL(Set.of("SNU", "CAU", "SOONGSIL")),
  HONGIK_YONSEI_EWHA(Set.of("HONGIK", "YONSEI", "EWHA")),
  KONKUK_SEJONG_HYU(Set.of("KONKUK", "SEJONG", "HYU")),
  ETC(Set.of());

  private final Set<String> memberCodes;

  UniversityGroup(Set<String> memberCodes) {
    this.memberCodes = memberCodes;
  }

  /** 그룹에 속한 개별 대학 코드(추천 매칭용). {@code ETC}는 빈 집합 — 고른 대학이 목록 밖이라 포함할 코드가 없다. */
  public Set<String> memberCodes() {
    return memberCodes;
  }

  /**
   * 사용자가 <b>특정 그룹을 골라 도달할 수 있는</b> 개별 대학 코드 전부다.
   *
   * <p>{@code ETC}("그 외 대학")를 고른 사용자에게 필요한 값이다 — 그 사용자의 학교는 목록에 없으므로, <b>이 집합 중 어느 것도 인근이 아닌</b>
   * 매물이 답이다(여집합 매칭). 여집합에는 목록 밖 대학 근처 매물과 대학가가 아닌 매물이 함께 들어온다.
   *
   * <p>{@code ETC}는 합산에서 <b>뺀다.</b> ETC는 대학의 묶음이 아니라 "위에 없음"이라는 답이라 멤버를 갖지 않지만, 누가 거기에 코드를 넣으면 그
   * 대학이 제외 집합에 들어가 <b>정확히 그 대학을 찾던 사용자에게서 매물이 사라진다</b> — 예외도 나지 않고 조용히 틀린다. 그룹 밖 대학은 카탈로그·원장에만 넣으면
   * 이 집합에 없으므로 여집합이 알아서 집어낸다.
   */
  public static Set<String> selectableMemberCodes() {
    return Arrays.stream(values())
        .filter(group -> group != ETC)
        .flatMap(group -> group.memberCodes.stream())
        .collect(Collectors.toUnmodifiableSet());
  }
}
