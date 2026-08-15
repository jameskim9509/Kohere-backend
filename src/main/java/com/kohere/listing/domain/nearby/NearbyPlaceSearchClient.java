package com.kohere.listing.domain.nearby;

import java.util.List;

/**
 * 매물 주변의 장소를 찾는 아웃바운드 포트다(ADR-0044).
 *
 * <p>두 메서드가 한 포트에 있는 이유는 <b>같은 제공자·같은 API 계열·같은 유스케이스</b>(등록 폼이 주변을 채운다)이기 때문이다. 둘로 쪼개면 HTTP 호출·좌표
 * 파싱·에러 래핑이 중복된다.
 *
 * <p>응용 계층은 "역 후보가 필요하다"는 의도만 알고, 카카오의 카테고리 코드나 페이지 파라미터는 인프라 어댑터에 숨긴다.
 *
 * <p><b>인근 대학은 여기 없다.</b> 제공자가 주는 장소 이름을 카탈로그 코드로 되돌리는 규칙이 서지 않아 ADR-0044가 뺐고, 결국 외부 조회 대신 서버가 가진
 * 좌표 원장으로 갔다({@code domain.university.UniversityRepository} · ADR-0045).
 */
public interface NearbyPlaceSearchClient {

  /**
   * 역 이름으로 지하철역 후보를 찾는다.
   *
   * @param keyword 검증과 trim이 끝난 검색어
   * @param origin 거리 계산·정렬 기준이 될 매물 좌표. {@code null}이면 정확도순이고 거리 정보가 없다
   * @return 제공자 순서를 유지한 역 후보 목록. 결과가 없으면 빈 목록
   * @throws NearbyPlaceSearchUpstreamException 외부 장애 또는 응답 계약 위반
   */
  List<NearbyPlace> searchStationsByKeyword(String keyword, Coordinate origin);

  /**
   * 좌표 주변의 지하철역을 가까운 순으로 찾는다.
   *
   * @param origin 매물 좌표
   * @return 반경 내 역 목록. 없으면 빈 목록
   * @throws NearbyPlaceSearchUpstreamException 외부 장애 또는 응답 계약 위반
   */
  List<NearbyPlace> searchNearbyStations(Coordinate origin);
}
