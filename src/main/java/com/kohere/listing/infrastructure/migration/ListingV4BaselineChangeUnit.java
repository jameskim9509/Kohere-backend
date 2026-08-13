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
 * listings v4 스키마 baseline이다. v1~v3 이행 체인({@code 0099}~{@code 0114})을 대체한다.
 *
 * <p>이 유닛은 <b>스키마만</b> 다룬다 — v4 {@code $jsonSchema} validator를 적용하고 키가 바뀐 옛 인덱스를 지운다. 시드 데이터는 운영자가
 * 수동으로 주입한다(docs/database/migration-policy.md §8-1).
 *
 * <p>v4 스키마 본문을 {@code ListingMongoIndexInitializer}에서 읽어오지 않고 이 클래스 안에 <b>동결</b>한다. 과거 {@code
 * 0105}가 정적 메서드를 호출한 탓에 v3에서 메서드를 포크해 죽은 사본이 생겼던 전례를 반복하지 않기 위해서다. v5는 새 changeUnit이 자기 스키마를 들고 오며
 * 이 파일은 수정하지 않는다.
 */
@ChangeUnit(id = "listing-v4-baseline", order = "0115", author = "kohere")
public class ListingV4BaselineChangeUnit {

  /** 키가 바뀌어 새 이름으로 다시 만드는 인덱스와, v4에서 근거가 사라진 인덱스다. */
  private static final List<String> OBSOLETE_INDEXES =
      List.of("listings_status_arc_required", "listings_status_room_available_count");

  @Execution
  public void execution(MongoTemplate mongo) {
    applyValidator(mongo);
    dropObsoleteIndexes(mongo);
  }

  /** 컬렉션이 없으면 validator를 달아 생성하고, 있으면 collMod로 교체한다. */
  private static void applyValidator(MongoTemplate mongo) {
    Document validator = new Document("$jsonSchema", listingV4JsonSchema());
    if (!mongo.collectionExists(ListingMigrationCollections.LISTINGS)) {
      mongo
          .getDb()
          .createCollection(
              ListingMigrationCollections.LISTINGS,
              new CreateCollectionOptions()
                  .validationOptions(
                      new ValidationOptions()
                          .validator(validator)
                          .validationLevel(ValidationLevel.STRICT)
                          .validationAction(ValidationAction.ERROR)));
      return;
    }
    mongo.executeCommand(
        new Document("collMod", ListingMigrationCollections.LISTINGS)
            .append("validator", validator)
            .append("validationLevel", "strict")
            .append("validationAction", "error"));
  }

  /**
   * 옛 인덱스를 지운다. 이름이 같고 키만 다른 인덱스는 부트스트랩의 멱등 생성으로 갱신되지 않고 {@code IndexOptionsConflict}가 나므로, 새 이름으로
   * 만들기 전에 옛 것을 먼저 지워야 한다.
   */
  private static void dropObsoleteIndexes(MongoTemplate mongo) {
    if (!mongo.collectionExists(ListingMigrationCollections.LISTINGS)) {
      return;
    }
    var collection = mongo.getCollection(ListingMigrationCollections.LISTINGS);
    for (Document index : collection.listIndexes()) {
      String name = index.getString("name");
      if (OBSOLETE_INDEXES.contains(name)) {
        collection.dropIndex(name);
      }
    }
  }

  /**
   * v4 저장 계약이다. 이 시점에 동결된 값이며 이후 스키마 개정은 새 changeUnit이 자기 사본을 들고 온다.
   *
   * <p>{@code required}에서 빠진 넷은 값이 없을 수 있는 필드다 — {@code blogUrl}(선택 입력), {@code rejectionReason}(반려
   * 시에만), {@code serviceFeedback}(선택 설문), {@code location}(지오코딩 전).
   */
  private static Document listingV4JsonSchema() {
    return new Document("bsonType", "object")
        .append(
            "required",
            List.of(
                "_id",
                "schemaVersion",
                "landlordId",
                "contact",
                "businessRegistrationNumber",
                "ageMin",
                "ageMax",
                "title",
                "type",
                "rentalType",
                "status",
                "genderPolicy",
                "languagesSupported",
                "arcRequired",
                "favoriteCount",
                "imageUrls",
                "nearbyUniversityCodes",
                "createdAt",
                "updatedAt",
                "address",
                "building",
                "description",
                "extraNotes",
                "facilities",
                "nearestTransit",
                "nearbyFacilities",
                "refundPolicy",
                "roomOffers",
                "preferredNationalities",
                "contractDifficulties"))
        .append(
            "properties",
            new Document("_id", bsonType("objectId"))
                .append("schemaVersion", new Document("enum", List.of(4)))
                .append("landlordId", bsonType("long"))
                .append("contact", contactSchema())
                .append("businessRegistrationNumber", bsonType("string"))
                .append("blogUrl", bsonType("string"))
                .append("ageMin", bsonType("int"))
                .append("ageMax", bsonType("int"))
                .append("title", localizedTextSchema())
                .append("type", bsonType("string"))
                .append("rentalType", bsonType("string"))
                .append("status", bsonType("string"))
                .append("rejectionReason", bsonType("string"))
                .append("genderPolicy", bsonType("string"))
                .append("languagesSupported", stringArray())
                .append("arcRequired", bsonType("string"))
                .append("favoriteCount", bsonType("int"))
                .append("imageUrls", stringArray())
                .append("nearbyUniversityCodes", stringArray())
                .append("createdAt", bsonType("date"))
                .append("updatedAt", bsonType("date"))
                .append("address", addressSchema())
                .append("building", buildingSchema())
                .append("description", localizedTextSchema())
                .append("extraNotes", localizedTextSchema())
                .append("facilities", facilitiesSchema())
                .append("location", bsonType("object"))
                .append("nearestTransit", nearestTransitSchema())
                .append("nearbyFacilities", stringArray())
                .append("refundPolicy", localizedTextSchema())
                .append("roomOffers", roomOffersSchema())
                .append("preferredNationalities", stringArray())
                .append("contractDifficulties", stringArray())
                .append("serviceFeedback", bsonType("string")));
  }

  private static Document contactSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("managerName", "phone", "sms"))
        .append(
            "properties",
            new Document("managerName", bsonType("string"))
                .append("phone", bsonType("string"))
                .append("sms", bsonType("string")));
  }

  private static Document addressSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("city", "district", "fullAddress"))
        .append(
            "properties",
            new Document("city", bsonType("string"))
                .append("district", bsonType("string"))
                .append("fullAddress", localizedTextSchema())
                .append("detail", localizedTextSchema()));
  }

  private static Document buildingSchema() {
    return new Document("bsonType", "object")
        .append(
            "required",
            List.of(
                "type",
                "usedFloorMin",
                "usedFloorMax",
                "totalFloors",
                "parkingAvailable",
                "elevatorAvailable"))
        .append(
            "properties",
            new Document("type", bsonType("string"))
                .append("usedFloorMin", bsonType("int"))
                .append("usedFloorMax", bsonType("int"))
                .append("totalFloors", bsonType("int"))
                .append("parkingAvailable", bsonType("bool"))
                .append("elevatorAvailable", bsonType("bool")));
  }

  private static Document facilitiesSchema() {
    return new Document("bsonType", "object")
        .append(
            "required",
            List.of(
                "heatingSystem",
                "kitchen",
                "laundry",
                "livingAmenities",
                "securityFeatures",
                "commonSpaces",
                "providedSupplies"))
        .append(
            "properties",
            new Document("heatingSystem", stringArray())
                .append("kitchen", stringArray())
                .append("laundry", stringArray())
                .append("livingAmenities", stringArray())
                .append("securityFeatures", stringArray())
                .append("commonSpaces", stringArray())
                .append("providedSupplies", stringArray()));
  }

  private static Document nearestTransitSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("type", "name", "walkMinutes"))
        .append(
            "properties",
            new Document("type", bsonType("string"))
                .append("name", localizedTextSchema())
                .append("walkMinutes", bsonType("int")));
  }

  private static Document roomOffersSchema() {
    Document contract =
        new Document("bsonType", "object")
            .append("required", List.of("minStayMonths", "maxStayMonths"))
            .append(
                "properties",
                new Document("minStayMonths", bsonType("int"))
                    .append("maxStayMonths", bsonType("int")));
    Document pricing =
        new Document("bsonType", "object")
            .append("required", List.of("monthlyRent", "deposit", "maintenanceFee", "currency"))
            .append(
                "properties",
                new Document("monthlyRent", bsonType("int"))
                    .append("deposit", bsonType("int"))
                    .append("maintenanceFee", bsonType("int"))
                    .append("currency", bsonType("string")));
    Document item =
        new Document("bsonType", "object")
            .append(
                "required",
                List.of(
                    "roomOfferId",
                    "name",
                    "status",
                    "contract",
                    "pricing",
                    "filterTags",
                    "roomImageUrls"))
            .append(
                "properties",
                new Document("roomOfferId", bsonType("string"))
                    .append("name", localizedTextSchema())
                    .append("status", bsonType("string"))
                    .append("contract", contract)
                    .append("pricing", pricing)
                    .append("filterTags", stringArray())
                    .append("roomImageUrls", stringArray()));
    return new Document("bsonType", "array").append("items", item);
  }

  private static Document localizedTextSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("ko", "en"))
        .append(
            "properties", new Document("ko", bsonType("string")).append("en", bsonType("string")));
  }

  private static Document stringArray() {
    return new Document("bsonType", "array").append("items", bsonType("string"));
  }

  private static Document bsonType(String type) {
    return new Document("bsonType", type);
  }

  /** forward-only: v4 저장 계약을 롤백으로 느슨하게 만들지 않는다. */
  @RollbackExecution
  public void rollback() {
    // no-op (forward-only)
  }
}
