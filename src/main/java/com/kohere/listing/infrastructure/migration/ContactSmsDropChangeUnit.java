package com.kohere.listing.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 담당자 연락처에서 문자문의 번호({@code contact.sms})를 뺀다(ADR-0039 Amended).
 *
 * <p>v4는 {@code contact}의 셋을 전부 필수로 받아 전부 세입자에게 공개했다. 그 근거는 "매물별 담당 연락처는 임대인 개인 연락처와 별개 값"이라는 것인데,
 * {@code sms}에서만은 그 전제가 깨진다 — 임대인이 그 칸에 적는 값은 온보딩에서 SMS 인증을 통과한 번호({@code users.phone_number})
 * 자신이라, ADR-0034가 마스킹 대상으로 정한 PII를 매물 응답으로 평문 공개하는 통로가 된다. 남는 {@code phone}은 지점 대표 전화라 매물마다 다르고,
 * 그제서야 "별개 값"이 참이 된다.
 *
 * <p><b>이행 대상 문서가 0건이다.</b> 시드 주입 전이라 {@code listings}에 실문서가 없어 필드를 지우는 배치가 없다(migration-policy
 * §8-1). 문서가 있었다면 {@code $unset} 배치가 먼저 와야 한다 — validator는 기존 문서를 소급 검사하지 않지만, {@code
 * additionalProperties}를 막지 않아도 {@code required}에서 빠진 필드가 남아 있는 문서는 다음 저장 때 도메인 매핑에서 조용히 유실된다.
 *
 * <p>{@code 0115}를 고치지 않고 새 유닛을 두는 이유는 그 파일이 <b>동결</b>이기 때문이다(migration-policy §8-2). 그래서 {@code
 * 0116}의 선례대로 이 유닛도 스키마 본문 사본을 직접 들고 {@code collMod}로 갈아 끼운다 — {@code 0115}·{@code 0116}의 사본에는 여전히
 * {@code sms}가 남아 있지만, 나중에 실행되는 이 유닛의 {@code collMod}가 그것을 덮으므로 앞의 둘을 손대지 않는다.
 *
 * <p><b>옛 validator가 되살아날 경로가 없다.</b> {@code ListingMongoIndexInitializer}는 매 기동마다 도는 {@code
 * ApplicationRunner}지만 인덱스만 만들고 validator를 소유하지 않는다({@code searchPlaces}가 컬렉션째 되살아나던 {@code 0117}의
 * 위험과 다른 지점이다). 게다가 Mongock이 {@code runner-type: InitializingBean}이라 같은 기동 안에서도 그 러너보다 먼저 끝난다.
 *
 * <p>{@code schemaVersion}은 4 그대로다. 필드 하나가 계약에서 빠질 뿐 문서의 세대가 바뀌지 않는다 — 재작성할 문서도 없다.
 */
@ChangeUnit(id = "listing-contact-sms-drop", order = "0119", author = "kohere")
public class ContactSmsDropChangeUnit {

  @Execution
  public void execution(MongoTemplate mongo) {
    if (!mongo.collectionExists(ListingMigrationCollections.LISTINGS)) {
      // 0115가 먼저 돌아 컬렉션을 만들므로 여기 도달하지 않는다. 순서가 흔들린 환경에서 조용히 통과하지 않도록 막는다.
      throw new IllegalStateException("listings 컬렉션이 없다 — 0115 listing-v4-baseline이 먼저 실행되어야 한다");
    }
    mongo.executeCommand(
        new Document("collMod", ListingMigrationCollections.LISTINGS)
            .append("validator", new Document("$jsonSchema", listingV4WithoutContactSmsSchema()))
            .append("validationLevel", "strict")
            .append("validationAction", "error"));
  }

  /**
   * {@code 0116}의 v4 스키마에서 {@code contact.sms}만 뺀 사본이다. 이 시점에 동결되며 이후 개정은 또 다른 changeUnit이 자기 사본을
   * 들고 온다.
   *
   * <p>{@code required}에서 빠진 셋은 값이 없을 수 있는 필드다 — {@code blogUrl}(선택 입력), {@code rejectionReason}(반려
   * 시에만), {@code serviceFeedback}(선택 설문).
   */
  private static Document listingV4WithoutContactSmsSchema() {
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
                "location",
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

  /** 담당자명과 지점 대표 전화 둘뿐이다. {@code sms}는 {@code required}에서도 {@code properties}에서도 사라진다. */
  private static Document contactSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("managerName", "phone"))
        .append(
            "properties",
            new Document("managerName", bsonType("string")).append("phone", bsonType("string")));
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

  /** forward-only: 저장 계약을 롤백으로 되돌려 PII 통로를 다시 열지 않는다. */
  @RollbackExecution
  public void rollback() {
    // no-op (forward-only)
  }
}
