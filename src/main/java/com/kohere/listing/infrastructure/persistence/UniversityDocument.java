package com.kohere.listing.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB {@code universities} 컬렉션의 저장 문서다(ADR-0045).
 *
 * <p>문서 하나가 대학 하나의 <b>좌표</b>를 설명한다. 표시 이름은 여기 두지 않는다 — 번역 정본은 {@code listingCatalog}의 {@code
 * UNIVERSITY} 카테고리이고, 같은 라벨을 두 컬렉션에 두면 한쪽만 고쳐지는 날이 온다. 두 컬렉션은 {@code code}로 조인한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = UniversityDocument.COLLECTION_NAME)
class UniversityDocument {

  static final String COLLECTION_NAME = "universities";

  /** 재시드해도 같은 문서를 덮어쓸 수 있도록 코드값을 그대로 id로 쓴다. */
  @Id private String id;

  /** {@code listingCatalog}·매물 {@code nearbyUniversityCodes}와 같은 값이다(조인 키). */
  private String code;

  /** 캠퍼스 대표 좌표. 반경 조회의 기준이라 값이 없는 문서는 저장 계약이 막는다. */
  private GeoJsonPoint location;
}
