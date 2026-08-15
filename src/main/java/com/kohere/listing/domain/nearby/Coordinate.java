package com.kohere.listing.domain.nearby;

import com.kohere.common.exception.InvalidInputException;

/**
 * 인근 장소를 찾는 기준이 되는 WGS84 좌표다(ADR-0044).
 *
 * <p>생성 시점에 범위를 확인해, 잘못된 좌표가 외부 호출까지 실려 가지 않게 한다.
 *
 * @param lat 위도(-90~90)
 * @param lng 경도(-180~180)
 */
public record Coordinate(double lat, double lng) {

  public Coordinate {
    if (lat < -90.0 || lat > 90.0) {
      throw new InvalidInputException("lat", "validation.range", -90, 90, lat);
    }
    if (lng < -180.0 || lng > 180.0) {
      throw new InvalidInputException("lng", "validation.range", -180, 180, lng);
    }
  }
}
