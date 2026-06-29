package com.kohere.diagnosis.infrastructure;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

/**
 * 진단 ③ 대학 그룹화·⑤ 월세 범위 도입에 따른 기존 사용자 진단({@code diagnoses}) 백필(ADR-0032 order 0001).
 *
 * <p>카탈로그({@code diagnosisQuestions}/{@code diagnosisSuggestions}) 베이스라인은 order 0000({@link
 * DiagnosisCatalogSeedChangeUnit})이 캐노니컬로 적재하므로, 본 changeUnit은 **사용자 데이터만** 이행한다 — 기존 {@code
 * diagnoses} 문서의 {@code university}(개별 대학 코드→그룹 코드 역매핑)·{@code monthlyBudgetMax}(→{@code
 * monthlyRentMax}, {@code monthlyRentMin}=0)를 in-place 백필한다(컬렉션 drop 없음, ADR-0005 D7). 그룹 멤버십은
 * {@link com.kohere.diagnosis.domain.UniversityGroup}와 동일하다.
 */
@ChangeUnit(id = "diagnosis-university-group-and-rent-range", order = "0001", author = "kohere")
public class DiagnosisGroupAndRentRangeChangeUnit {

  private static final String DIAGNOSES = "diagnoses";

  /** 개별 대학 코드 → 소속 그룹 코드(역매핑). {@code ETC}는 이미 그룹 코드라 제외한다. */
  private static final Map<String, String> UNIVERSITY_TO_GROUP =
      Map.ofEntries(
          Map.entry("HUFS", "HUFS_KHU_KOREA"),
          Map.entry("KHU", "HUFS_KHU_KOREA"),
          Map.entry("KOREA", "HUFS_KHU_KOREA"),
          Map.entry("SKKU", "SKKU_SUNGSHIN"),
          Map.entry("SUNGSHIN", "SKKU_SUNGSHIN"),
          Map.entry("SNU", "SNU_CAU_SOONGSIL"),
          Map.entry("CAU", "SNU_CAU_SOONGSIL"),
          Map.entry("SOONGSIL", "SNU_CAU_SOONGSIL"),
          Map.entry("HONGIK", "HONGIK_YONSEI_EWHA"),
          Map.entry("YONSEI", "HONGIK_YONSEI_EWHA"),
          Map.entry("EWHA", "HONGIK_YONSEI_EWHA"),
          Map.entry("KONKUK", "KONKUK_SEJONG_HYU"),
          Map.entry("SEJONG", "KONKUK_SEJONG_HYU"),
          Map.entry("HYU", "KONKUK_SEJONG_HYU"));

  /** 기존 진단 문서의 대학(개별→그룹)·예산(단일 상한→월세 범위)을 in-place 백필한다. */
  @Execution
  public void execution(MongoTemplate mongo) {
    UNIVERSITY_TO_GROUP.forEach(
        (individual, group) ->
            mongo.updateMulti(
                query(where("university").is(individual)),
                new Update().set("university", group),
                DIAGNOSES));
    mongo.updateMulti(
        query(where("monthlyBudgetMax").exists(true)),
        new Update().rename("monthlyBudgetMax", "monthlyRentMax").set("monthlyRentMin", 0),
        DIAGNOSES);
  }

  /** forward-only: 데이터 이행은 되돌리지 않는다(Mongock이 메서드 존재만 요구). */
  @RollbackExecution
  public void rollback() {
    // no-op (forward-only)
  }
}
