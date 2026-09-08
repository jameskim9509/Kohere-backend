package com.kohere.diagnosis.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * v1 진단 경로가 남긴 저장소 잔해를 걷어낸다 — 조정 제안 컬렉션, 죽은 문항 인덱스, 그리고 옛 진단 문서 전량.
 *
 * <p><b>진단 문서를 전량 지운다.</b> 이 컬렉션은 아직 버려도 되는 시험 데이터이고, 남겨 두면 도메인이 더 이상 모르는 값이 섞인 채로 계속 조회된다 — 과거
 * 마이그레이션이 {@code conditions}에 넣은 {@code NO_ARC}가 그것이다(운영 실측 40건). 그 상수를 추가했던 커밋이 나중에 상수만 되돌리고 데이터는
 * 두고 갔다.
 *
 * <p><b>그 값이 조회를 깨뜨리지는 않는다</b> — 드라이버가 미등록 enum 원소를 조용히 빼고 읽으므로 응답에서 사라질 뿐 오류가 되지 않는다 (그 성질은 {@code
 * DiagnosisQueryServiceIntegrationTest}가 원시 문서로 고정한다). 그래서 이 삭제는 장애 수습이 아니라 위생 작업이며, 읽기 쪽에 미등록 값을
 * 걸러 내는 방어를 따로 두지 않는다. 지우고 나면 같은 값이 다시 쌓일 수도 없다 — {@code NO_ARC}를 {@code conditions}에 넣을 수 있는 코드
 * 경로가 0이다.
 *
 * <p><b>진행 세션({@code diagnosisFlowSessions})은 건드리지 않는다.</b> 스스로 낫는다 — {@code POST
 * /api/v2/diagnoses/start}가 사용자의 세션을 통째로 갈아끼우므로 고아 세션은 다음 진단 시작 때 사라진다.
 *
 * <p><b>인덱스는 하나만 지운다.</b> {@code diagnoses}의 {@code (userId, submittedAt)}는 이력·최근 조회가 계속 타므로 남기고,
 * {@code diagnosisQuestions}의 {@code (active, step)}만 지운다 — 문항 문서에 {@code step} 필드가 없어 v1 제거 이전부터
 * 아무 질의도 타지 않던 인덱스다.
 *
 * <p><b>되돌리지 않는다.</b> 지운 문서는 복구할 수 없고 {@code @RollbackExecution}은 no-op이다 — forward-only
 * (migration-policy §1).
 */
@ChangeUnit(id = "diagnosis-v1-retire", order = "0124", author = "kohere")
public class DiagnosisV1RetireChangeUnit {

  private static final String DIAGNOSES = "diagnoses";
  private static final String DIAGNOSIS_QUESTIONS = "diagnosisQuestions";
  private static final String DIAGNOSIS_SUGGESTIONS = "diagnosisSuggestions";
  private static final String DEAD_QUESTION_INDEX = "active_step_idx";

  @Execution
  public void execution(MongoTemplate mongo) {
    // 조정 제안은 v1 추천이 0건일 때만 쓰던 자산이다. 그 경로가 사라져 읽는 코드가 0이 됐다.
    if (mongo.collectionExists(DIAGNOSIS_SUGGESTIONS)) {
      mongo.getCollection(DIAGNOSIS_SUGGESTIONS).drop();
    }
    // 시험 데이터이므로 전량 삭제한다. 컬렉션 자체는 남긴다 — 새 진단이 계속 쌓이는 곳이고,
    // 인덱스도 여기 붙어 있어 drop하면 초기화기가 다음 기동에 다시 만들 때까지 비어 있게 된다.
    if (mongo.collectionExists(DIAGNOSES)) {
      mongo.getCollection(DIAGNOSES).deleteMany(new Document());
    }
    dropIndexQuietly(mongo);
  }

  /**
   * 인덱스가 이미 없으면 드라이버가 {@code IndexNotFound}로 던진다. 신규 환경에서는 애초에 만들어진 적이 없으므로 없는 것이 정상이고, 그 때문에
   * 마이그레이션 전체가 실패해 기동이 멈추면 안 된다.
   */
  private static void dropIndexQuietly(MongoTemplate mongo) {
    if (!mongo.collectionExists(DIAGNOSIS_QUESTIONS)) {
      return;
    }
    try {
      mongo.getCollection(DIAGNOSIS_QUESTIONS).dropIndex(DEAD_QUESTION_INDEX);
    } catch (RuntimeException e) {
      // 없으면 지울 것도 없다.
    }
  }

  @RollbackExecution
  public void rollback(MongoTemplate mongo) {
    // 지운 문서는 되돌리지 않는다 — forward-only(migration-policy §1).
  }
}
