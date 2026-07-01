package com.kohere.diagnosis.domain;

import java.util.Set;

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

  /** 그룹에 속한 개별 대학 코드(추천 매칭용). {@code ETC}는 빈 집합 — 대학 필터를 적용하지 않는다. */
  public Set<String> memberCodes() {
    return memberCodes;
  }
}
