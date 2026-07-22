package com.kohere.diagnosis.infrastructure;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * v2 진행 세션 컬렉션({@code diagnosisFlowSessions}) 인덱스 멱등 생성(기동 시). Spring Boot 3.5(Spring Data MongoDB
 * 4.x)는 자동 인덱스 생성이 기본 비활성이라 도큐먼트 선언만으로는 인덱스가 만들어지지 않으므로 부트스트랩에서 명시적으로 보장한다(migration-policy §8 — 멱등
 * 생성).
 *
 * <p><b>UNIQUE는 신원별 partial 둘</b>이다(#181) — 회원 세션은 {@code userId}가, 게스트 세션은 {@code guestSessionId}가
 * 채워지므로 하나의 UNIQUE 인덱스로 합칠 수 없다. 합치면 게스트 문서들의 {@code userId} 부재가 서로 같은 값으로 취급되어 <b>두 번째 게스트부터 세션
 * 생성이 중복 키로 실패</b>한다. 그래서 각 인덱스를 자기 신원 필드가 존재하는 문서로만 좁힌다. 회원 경로의 제약("사용자당 1 세션")은 그대로다.
 *
 * <p><b>레거시 인덱스 교체</b>: 이미 배포된 환경에는 partial이 아닌 UNIQUE {@code userId_unique_idx}가 있고, MongoDB는 같은
 * 이름에 다른 옵션으로 {@code createIndex}를 하면 {@code IndexOptionsConflict}로 거절한다. 그래서 새 인덱스는 <b>새 이름</b>으로
 * 만들고 레거시 이름은 있으면 drop한다 — 이름이 달라 충돌 자체가 발생하지 않고, 재기동 시에는 drop 대상이 없고 같은 이름·같은 정의라 createIndex가
 * 무연산이다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
class DiagnosisFlowSessionIndexInitializer implements ApplicationRunner {

  private static final String COLLECTION = "diagnosisFlowSessions";

  /** #181 이전의 비-partial UNIQUE 인덱스 이름. 존재하면 partial 인덱스로 교체한다. */
  private static final String LEGACY_USER_ID_INDEX = "userId_unique_idx";

  private static final String USER_ID_INDEX = "userId_partial_unique_idx";
  private static final String GUEST_SESSION_ID_INDEX = "guestSessionId_partial_unique_idx";

  private final MongoOperations mongoOperations;

  @Override
  public void run(ApplicationArguments args) {
    IndexOperations indexOps = mongoOperations.indexOps(COLLECTION);

    // Mongo 가용성 확인을 겸한 탐침. 여기서 실패하면 종전대로 기동을 막지 않는다(로컬·CI에서 Mongo 없이 뜨는 경우).
    List<IndexInfo> existing;
    try {
      existing = indexOps.getIndexInfo();
    } catch (RuntimeException e) {
      log.warn("진단 흐름 세션 인덱스 생성을 생략한다(Mongo 미가용 등): {}", e.getMessage());
      return;
    }

    // 여기부터는 Mongo에 닿은 것이 확인된 상태다 — 실패를 삼키지 않고 기동을 중단시킨다.
    // 삼키면 인덱스가 조용히 레거시 정의로 남고, 그 결과는 기동이 아니라 "두 번째 게스트의 세션 생성 실패"라는
    // 런타임 데이터 오류로만 드러난다. 인덱스 정합화 실패는 배포 시점에 보여야 한다.
    try {
      dropLegacyUserIdIndex(existing, indexOps);
      indexOps.createIndex(
          new Index()
              .on("userId", Sort.Direction.ASC)
              .unique()
              .named(USER_ID_INDEX)
              .partial(PartialIndexFilter.of(Criteria.where("userId").exists(true))));
      indexOps.createIndex(
          new Index()
              .on("guestSessionId", Sort.Direction.ASC)
              .unique()
              .named(GUEST_SESSION_ID_INDEX)
              .partial(PartialIndexFilter.of(Criteria.where("guestSessionId").exists(true))));
      log.info("Ensured diagnosisFlowSessions MongoDB indexes");
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "diagnosisFlowSessions 인덱스 정합화에 실패했다 — 게스트 세션 생성이 런타임에 중복 키로 깨진다.", e);
    }
  }

  /**
   * 레거시 비-partial UNIQUE 인덱스를 제거한다(있을 때만). 새 인덱스는 이름이 달라 이 drop 없이도 만들어지지만, 남겨 두면 레거시 인덱스가 계속 게스트
   * 문서들의 {@code userId} 부재를 중복으로 판정해 목적이 달성되지 않는다.
   */
  private static void dropLegacyUserIdIndex(List<IndexInfo> existing, IndexOperations indexOps) {
    boolean present = existing.stream().anyMatch(i -> LEGACY_USER_ID_INDEX.equals(i.getName()));
    if (!present) {
      return;
    }
    indexOps.dropIndex(LEGACY_USER_ID_INDEX);
    log.info("Dropped legacy non-partial index {} on {}", LEGACY_USER_ID_INDEX, COLLECTION);
  }
}
