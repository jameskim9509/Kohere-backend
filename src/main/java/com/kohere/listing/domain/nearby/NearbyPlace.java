package com.kohere.listing.domain.nearby;

/**
 * 매물 주변에서 찾은 장소 하나다 — 지하철역과 대학이 같은 모양을 쓴다(ADR-0044).
 *
 * <p>제공자 필드명을 지운 내부 값이다. 카카오의 {@code id}·{@code place_url}·{@code category_name} 같은 값은 여기에 담지 않는다 —
 * 제공자 식별자를 도메인에 들이면 제공자를 바꿀 때 응답 계약까지 함께 깨진다.
 *
 * @param name 장소 이름. 역은 카카오 표기 그대로, 대학은 본명으로 정규화된 값이다
 * @param roadAddress 도로명 주소. 제공되지 않으면 빈 문자열
 * @param jibunAddress 지번 주소. 제공되지 않으면 빈 문자열
 * @param lat WGS84 위도
 * @param lng WGS84 경도
 * @param distanceMeters 기준 좌표에서의 직선거리(m). 좌표를 주지 않은 검색이면 {@code null}
 */
public record NearbyPlace(
    String name,
    String roadAddress,
    String jibunAddress,
    double lat,
    double lng,
    Integer distanceMeters) {}
