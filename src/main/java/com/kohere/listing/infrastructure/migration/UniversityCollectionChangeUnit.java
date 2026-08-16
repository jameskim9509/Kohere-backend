package com.kohere.listing.infrastructure.migration;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 대학 좌표 원장 {@code universities}의 저장 계약을 세운다(ADR-0045).
 *
 * <p>이 유닛은 <b>스키마만</b> 다룬다 — {@code $jsonSchema} validator를 적용할 뿐 문서를 넣지 않는다. 시드 14건은 운영자가 정본
 * JSON으로 주입한다(migration-policy §8-1). 캠퍼스가 옮겨가거나 코드가 늘 때 재빌드·재배포 없이 고치기 위해서다.
 *
 * <p>{@code location}은 {@code required}다. 좌표 없는 대학 문서는 반경 조회에서 영영 잡히지 않는 죽은 행이라, 넣는 순간 막는 편이 낫다.
 *
 * <p>스키마 본문은 이 클래스 안에 <b>동결</b>한다. 공용 정적 메서드를 부르면 다음 개정 때 사본이 갈라져 죽은 코드가 남는다({@code 0105}의 전례 —
 * {@code 0115}·{@code 0116}이 같은 이유로 각자 사본을 든다).
 *
 * <p>인덱스는 여기서 만들지 않는다. 지오 인덱스 소유는 부트스트랩({@code ListingMongoIndexInitializer})이며 changeUnit은 옛 인덱스를
 * 지울 때만 손댄다(migration-policy §8-2).
 */
@ChangeUnit(id = "listing-university-collection", order = "0118", author = "kohere")
public class UniversityCollectionChangeUnit {

  @Execution
  public void execution(MongoTemplate mongo) {
    Document validator = new Document("$jsonSchema", universityJsonSchema());
    if (!mongo.collectionExists(ListingMigrationCollections.UNIVERSITIES)) {
      mongo
          .getDb()
          .createCollection(
              ListingMigrationCollections.UNIVERSITIES,
              new CreateCollectionOptions()
                  .validationOptions(
                      new ValidationOptions()
                          .validator(validator)
                          .validationLevel(ValidationLevel.STRICT)
                          .validationAction(ValidationAction.ERROR)));
      return;
    }
    mongo.executeCommand(
        new Document("collMod", ListingMigrationCollections.UNIVERSITIES)
            .append("validator", validator)
            .append("validationLevel", "strict")
            .append("validationAction", "error"));
  }

  /**
   * 대학 원장 문서의 저장 계약이다.
   *
   * <p>{@code _id}는 코드값 문자열이다({@code listingCatalog}가 {@code CATEGORY:CODE}를 쓰는 것과 같은 이유 — 재시드가 같은
   * 문서를 덮어써야 한다). 라벨은 담지 않는다: 번역 정본은 {@code listingCatalog}이고 {@code code}로 조인한다.
   */
  private static Document universityJsonSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("_id", "code", "location"))
        .append(
            "properties",
            new Document("_id", bsonType("string"))
                .append("code", bsonType("string"))
                .append("location", locationSchema()));
  }

  /** GeoJSON Point. 좌표 순서는 매물 {@code location}과 같은 {@code [경도, 위도]}다. */
  private static Document locationSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("type", "coordinates"))
        .append(
            "properties",
            new Document("type", new Document("enum", List.of("Point")))
                .append(
                    "coordinates",
                    new Document("bsonType", "array")
                        .append("minItems", 2)
                        .append("maxItems", 2)
                        .append("items", bsonType("double"))));
  }

  private static Document bsonType(String type) {
    return new Document("bsonType", type);
  }

  /** forward-only: 저장 계약을 롤백으로 다시 느슨하게 만들지 않는다. */
  @RollbackExecution
  public void rollback() {
    // no-op (forward-only)
  }
}
