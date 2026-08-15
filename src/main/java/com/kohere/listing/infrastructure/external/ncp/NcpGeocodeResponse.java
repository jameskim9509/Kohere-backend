package com.kohere.listing.infrastructure.external.ncp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * NCP Maps Geocoding JSON 중 Kohere가 실제로 사용하는 필드만 역직렬화하는 인프라 DTO다.
 *
 * <p>{@code meta}·{@code distance}·{@code addressElements}처럼 프론트 계약에 필요 없는 필드는 무시한다. 이 타입은 NCP 스키마가
 * 응용·도메인 계층으로 새어 나가지 않도록 인프라 패키지 안에만 둔다.
 *
 * @param status 처리 결과. 정상은 {@code OK}이며 {@code INVALID_REQUEST}·{@code SYSTEM_ERROR}는 상류 오류다
 * @param addresses 주소 후보 목록. 결과가 없어도 빈 배열로 오며 {@code null}은 계약 위반이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record NcpGeocodeResponse(String status, List<Address> addresses) {

  /** 주소 한 건의 원본 필드다. 좌표는 API가 문자열로 내려주므로 그대로 받은 뒤 명시적으로 변환한다({@code x}=경도, {@code y}=위도). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Address(
      String roadAddress, String jibunAddress, String englishAddress, String x, String y) {}
}
