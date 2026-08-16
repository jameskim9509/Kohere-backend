package com.kohere.listing.domain.university;

import com.kohere.listing.domain.nearby.Coordinate;
import java.util.Set;

/**
 * 좌표 주변의 대학 코드를 찾는 도메인 포트다(ADR-0045).
 *
 * <p>대학 좌표는 외부 제공자에 묻지 않고 서버가 시드로 가진 원장({@code universities})에서 읽는다. 제공자 응답의 장소 이름을 카탈로그 코드로 되돌리는
 * 규칙이 필요 없어지기 때문이다 — 원장은 코드 자체를 키로 들고 있다(ADR-0044가 인근 대학 매핑을 뺀 이유가 그 이름 매칭이었다).
 *
 * <p>반환값이 곧 매물의 {@code nearbyUniversityCodes}이고, 진단 추천은 그 값을 대학 그룹의 멤버 코드와 대조한다.
 */
public interface UniversityRepository {

  /**
   * 기준 좌표에서 반경 안에 있는 대학 코드를 모두 반환한다.
   *
   * <p>가까운 순서를 약속하지 않는다 — 결과는 집합이고, 매칭은 포함 여부만 본다.
   *
   * @param origin 기준 좌표(매물 위치)
   * @param radiusMeters 반경(m)
   * @return 반경 안의 대학 코드. 없거나 원장이 비어 있으면 빈 집합
   */
  Set<String> findCodesWithin(Coordinate origin, int radiusMeters);
}
