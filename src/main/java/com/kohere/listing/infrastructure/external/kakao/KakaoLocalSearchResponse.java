package com.kohere.listing.infrastructure.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 로컬 검색 JSON 중 Kohere가 실제로 사용하는 필드만 역직렬화하는 인프라 DTO다(ADR-0044).
 *
 * <p>키워드 검색과 카테고리 검색의 응답 구조가 같아 한 타입으로 받는다. {@code meta}·{@code id}·{@code place_url}·{@code
 * phone}처럼 프론트 계약에 필요 없는 필드는 무시한다. 이 타입은 카카오 스키마가 응용·도메인 계층으로 새어 나가지 않도록 인프라 패키지 안에만 둔다.
 *
 * @param documents 검색 결과 목록. 결과가 없어도 빈 배열로 오며 {@code null}은 계약 위반이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoLocalSearchResponse(List<Document> documents) {

  /**
   * 장소 한 건의 원본 필드다. 좌표와 거리는 API가 문자열로 내려주므로 그대로 받은 뒤 명시적으로 변환한다({@code x}=경도, {@code y}=위도).
   *
   * @param placeName 장소명
   * @param categoryName {@code "교육,학문 > 학교 > 대학교"}처럼 {@code >}로 이어진 계층 분류. 대학 판별에만 쓰고 밖으로 내보내지 않는다
   * @param categoryGroupCode 카테고리 그룹 코드. 요청한 코드와 다른 문서를 방어적으로 걸러 내는 데 쓴다
   * @param roadAddressName 도로명 주소
   * @param addressName 지번 주소
   * @param x 경도(문자열)
   * @param y 위도(문자열)
   * @param distance 중심 좌표에서의 직선거리(m, 문자열). x·y를 보낸 요청에만 온다
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Document(
      @JsonProperty("place_name") String placeName,
      @JsonProperty("category_name") String categoryName,
      @JsonProperty("category_group_code") String categoryGroupCode,
      @JsonProperty("road_address_name") String roadAddressName,
      @JsonProperty("address_name") String addressName,
      String x,
      String y,
      String distance) {}
}
