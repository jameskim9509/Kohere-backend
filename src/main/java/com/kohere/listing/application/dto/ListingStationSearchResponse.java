package com.kohere.listing.application.dto;

import java.util.List;

/**
 * 인근 역 검색 응답이다(ADR-0044).
 *
 * <p>이름 검색과 좌표 검색이 같은 구조를 쓴다. 결과가 없으면 {@code items=[]}를 반환해 정상적인 빈 검색과 외부 장애를 구분한다.
 *
 * @param items 등록 폼의 역 후보 목록
 */
public record ListingStationSearchResponse(List<Item> items) {

  /**
   * 임대인이 후보를 고르고, 그 이름을 등록 요청에 담는 데 필요한 필드다.
   *
   * @param name 역 이름. 등록 요청의 {@code nearestTransit.name}에 그대로 담는다
   * @param roadAddress 역 출입구 도로명 주소. 후보 구분용 보조 표시이며 등록에는 보내지 않는다
   * @param jibunAddress 지번 주소. 보조 표시용
   * @param lat WGS84 십진수 위도. 폼이 지도에 핀을 찍는 용도이며 등록에는 보내지 않는다
   * @param lng WGS84 십진수 경도
   * @param distanceMeters 매물 좌표에서의 직선거리(m). 좌표를 주지 않은 요청이면 {@code null}
   * @param suggestedWalkMinutes 도보 시간 제안값(분). {@code distanceMeters}가 있을 때만 채워진다
   */
  public record Item(
      String name,
      String roadAddress,
      String jibunAddress,
      double lat,
      double lng,
      Integer distanceMeters,
      Integer suggestedWalkMinutes) {}
}
