package com.kohere.listing.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 시드 POI 사전 컬렉션({@code searchPlaces})을 드롭한다(ADR-0043).
 *
 * <p>이 컬렉션을 쓰던 키워드 검색 API가 v1·v2 양쪽에서 제거되면서 읽는 코드가 하나도 남지 않았다. 시드를 넣던 {@code 0100
 * listing-search-place-seed}도 함께 삭제됐으므로 기존 환경의 changelog에는 그 항목이 고아로 남지만, 대응 클래스가 없으면 실행 대상에서 빠질
 * 뿐이라 무해하다.
 *
 * <p><b>수동 드롭 대신 changeUnit을 쓰는 이유</b>는 컬렉션이 되살아나는 것을 막기 위해서다. {@code
 * ListingMongoIndexInitializer}는 마이그레이션이 아니라 매 기동마다 도는 {@code ApplicationRunner}이고 {@code
 * createIndex}는 대상 컬렉션이 없으면 만든다 — 손으로 지워도 다음 기동에 빈 컬렉션이 인덱스와 함께 돌아온다. 드롭을 코드에 두면 인덱스 블록 제거와 <b>같은
 * 배포</b>에 실려 나가고, Mongock이 {@code runner-type: InitializingBean}이라 같은 기동 안에서도 {@code
 * ApplicationRunner} 보다 먼저 끝난다.
 *
 * <p>새 환경은 {@code 0100}이 돈 적이 없어 컬렉션 자체가 없다. 없는 컬렉션 드롭은 실패가 아니라 건너뛴다.
 */
@ChangeUnit(id = "listing-search-place-drop", order = "0117", author = "kohere")
public class SearchPlaceDropChangeUnit {

  @Execution
  public void execution(MongoTemplate mongo) {
    if (mongo.collectionExists(ListingMigrationCollections.SEARCH_PLACES)) {
      mongo.dropCollection(ListingMigrationCollections.SEARCH_PLACES);
    }
  }

  /** forward-only: 되살릴 시드가 코드에서 이미 사라졌다(Mongock이 메서드 존재만 요구). */
  @RollbackExecution
  public void rollback() {
    // no-op
  }
}
