package com.kohere.listing.application.dto;

import java.util.List;

/**
 * 도로명 주소 검색 응답이다(ADR-0042).
 *
 * <p>검색 결과는 외부 제공자가 반환한 순서를 유지하며 최대 5개다. 결과가 없으면 {@code items=[]}를 반환해 정상적인 빈 검색과 외부 장애를 구분한다.
 *
 * @param items 등록 폼의 주소 후보 목록
 */
public record ListingAddressSearchResponse(List<Item> items) {

  /**
   * 임대인이 후보를 고르고, 그 값을 등록 요청에 그대로 되돌려 보내는 데 필요한 필드다.
   *
   * @param roadAddress 도로명 주소. 등록 요청의 {@code address.fullAddress}에 그대로 담는다
   * @param jibunAddress 지번 주소. 후보 구분용 보조 표시이며 등록에는 보내지 않는다
   * @param englishAddress 영문 표기. 보조 표시용
   * @param lat WGS84 십진수 위도. 등록 요청의 {@code address.lat}에 그대로 담는다
   * @param lng WGS84 십진수 경도. 등록 요청의 {@code address.lng}에 그대로 담는다
   */
  public record Item(
      String roadAddress, String jibunAddress, String englishAddress, double lat, double lng) {}
}
