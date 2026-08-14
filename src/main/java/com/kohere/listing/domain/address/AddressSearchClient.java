package com.kohere.listing.domain.address;

import java.util.List;

/**
 * 도로명 주소 검색의 아웃바운드 포트다(ADR-0042).
 *
 * <p>도메인은 이 인터페이스만 알고 HTTP·제공자 SDK는 인프라 어댑터에 가둔다({@code
 * listing.infrastructure.external.ncp.NcpGeocodeClient}) — {@code PlaceSearchClient}·{@code
 * ListingImageStorage}와 같은 구조다.
 */
public interface AddressSearchClient {

  /**
   * 주소 문자열로 도로명 주소 후보를 찾는다.
   *
   * @param query 검증과 trim이 끝난 검색어
   * @return 제공자 순서를 유지한 후보 목록. 일치하는 주소가 없으면 빈 목록
   * @throws AddressSearchUpstreamException 외부 장애·응답 계약 위반·인증정보 누락
   */
  List<AddressSearchResult> search(String query);
}
