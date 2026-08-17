package com.kohere.listing.infrastructure.persistence;

import com.kohere.listing.domain.nearby.Coordinate;
import com.kohere.listing.domain.university.UniversityRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** 시드된 대학 좌표 원장에서 반경 안의 코드를 읽는 저장소 어댑터다(ADR-0045). */
@Repository
@RequiredArgsConstructor
class UniversityRepositoryImpl implements UniversityRepository {

  private static final double METERS_PER_KILOMETER = 1000.0;

  private final MongoTemplate mongoTemplate;

  /**
   * {@code $geoWithin}+{@code $centerSphere}로 반경 안의 문서를 읽는다.
   *
   * <p>거리순 정렬이 필요 없어 {@code $nearSphere}를 쓰지 않는다 — 반환값은 집합이고, {@code $nearSphere}는 2dsphere 인덱스가
   * 없으면 조회 자체가 실패하는 반면 {@code $geoWithin}은 인덱스 없이도 답을 낸다. 인덱스는 성능으로만 기여한다.
   *
   * <p>{@code code} 하나만 투영한다. 좌표를 도메인으로 끌어올릴 이유가 없다.
   */
  @Override
  public Set<String> findCodesWithin(Coordinate origin, int radiusMeters) {
    Circle radius =
        new Circle(
            new Point(origin.lng(), origin.lat()),
            new Distance(radiusMeters / METERS_PER_KILOMETER, Metrics.KILOMETERS));
    Query query = new Query(Criteria.where("location").withinSphere(radius));
    query.fields().include("code");
    return mongoTemplate.find(query, UniversityDocument.class).stream()
        .map(UniversityDocument::getCode)
        .collect(Collectors.toUnmodifiableSet());
  }
}
