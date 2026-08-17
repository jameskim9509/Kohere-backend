package com.kohere.listing.api;

import java.util.Set;

/**
 * 매물 추천 조건(모듈 간 전달용 값객체). diagnosis가 저장된 진단 조건으로 구성해 {@link ListingRecommendationService}에 넘긴다.
 *
 * <p>모듈 간 enum은 원시 문자열로 주고받는다(domain-model §1·§2) — {@code region}/{@code conditions}/{@code
 * district}는 diagnosis 소유 enum의 이름(UPPER_SNAKE) 문자열이고, listing이 자기 enum으로 해석한다. 대학은 diagnosis가
 * {@code UniversityGroup}을 개별 대학 코드로 펼친 뒤 전달하므로 listing은 그룹 enum을 알 필요가 없다.
 *
 * <p>③ 대학·지역은 입국 목적 분기에 따라 한쪽 기준만 실질적으로 채워진다. STUDY는 {@code includedUniversityCodes}로, NON_STUDY는
 * {@code district}로 좁힌다.
 *
 * <p>대학 조건은 두 방향으로 온다 — 특정 그룹을 고르면 그 멤버 코드가 {@code includedUniversityCodes}에 담겨 <b>포함</b> 매칭이 되고,
 * "그 외 대학"({@code ETC})을 고르면 목록에 든 코드 전부가 {@code excludedUniversityCodes}에 담겨 <b>제외</b> 매칭이 된다. 둘 다
 * 비어 있으면 대학 필터가 없다(비유학 등). 진단 지역({@code district})의 {@code ETC}가 명시 5구의 여집합인 것과 같은 규칙이다.
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md §7(RecommendationCriteria).
 *
 * @param region 진단 ① 지역(단일, 예: {@code "SEOUL"})
 * @param monthlyRentMin 진단 ⑤ 월세 하한(KRW 정수, {@code null}=하한 없음)
 * @param monthlyRentMax 진단 ⑤ 월세 상한(KRW 정수, {@code null}=상한 없음)
 * @param conditions 진단 ④ 주거 조건과 파생 매물 필터 이름 집합(0~다수, 예: {@code "PRIVATE_BATH"}, {@code "NO_ARC"})
 * @param includedUniversityCodes 진단 ③ 대학 그룹에서 펼친 개별 대학 코드 집합(예: {@code "SNU"}, {@code "CAU"}) — 이 중
 *     하나라도 인근이면 매칭
 * @param excludedUniversityCodes ③에서 "그 외 대학"({@code ETC})을 골랐을 때 제외할 코드 집합(목록의 전체 대학) — 이 중 어느 것도
 *     인근이 아니어야 매칭
 * @param district 진단 ③ 지역(구)(NON_STUDY일 때, 그 외 {@code null}, 예: {@code "GURO_GU"})
 * @param page 0-base 페이지 번호(오프셋 페이지네이션)
 * @param size 페이지 크기(최대 100)
 * @param sort 정렬 키({@code field,(asc|desc)}; 허용: {@code recommended}/{@code price}/{@code
 *     distance})
 */
public record RecommendationCriteria(
    String region,
    Integer monthlyRentMin,
    Integer monthlyRentMax,
    Set<String> conditions,
    Set<String> includedUniversityCodes,
    Set<String> excludedUniversityCodes,
    String district,
    String arcStatus,
    int page,
    int size,
    String sort) {

  public RecommendationCriteria {
    conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    includedUniversityCodes =
        includedUniversityCodes == null ? Set.of() : Set.copyOf(includedUniversityCodes);
    excludedUniversityCodes =
        excludedUniversityCodes == null ? Set.of() : Set.copyOf(excludedUniversityCodes);
    if (!includedUniversityCodes.isEmpty() && !excludedUniversityCodes.isEmpty()) {
      // 포함과 제외를 동시에 주면 어느 쪽이 이기는지 호출자마다 다르게 기대한다. 진단은 둘 중 하나만 채운다.
      throw new IllegalArgumentException("대학 포함 조건과 제외 조건은 동시에 올 수 없다");
    }
  }
}
