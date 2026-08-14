package com.kohere.listing.domain.address;

/**
 * 외부 지오코딩 제공자가 돌려준 도로명 주소 후보 하나다.
 *
 * <p>제공자 필드명(NCP의 {@code x}·{@code y})을 걷어낸 값이라 어댑터가 바뀌어도 상위 계층이 흔들리지 않는다. 좌표는 문자열이 아니라 WGS84 십진수로
 * 변환된 뒤 여기 담긴다.
 *
 * <p>등록 가능 여부({@code supported})는 여기 없다 — 그 판정은 제공자가 아니라 우리 코드 카탈로그가 하므로 응용 계층이 붙인다.
 *
 * @param roadAddress 도로명 주소. 건물명이 붙어 있을 수 있으며 다듬지 않는다
 * @param jibunAddress 지번 주소. 사용자가 후보를 구분하는 보조 정보다
 * @param englishAddress 영문 표기
 * @param lat 위도(WGS84)
 * @param lng 경도(WGS84)
 */
public record AddressSearchResult(
    String roadAddress, String jibunAddress, String englishAddress, double lat, double lng) {}
