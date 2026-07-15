# ADR-0036. 진단 질의응답을 서버 주도(next) 흐름으로 /api/v2에 신설하고 진행 상태를 별도 flow-session에 둔다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0036 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-07-15 |
| 관련 문서 | [ADR-0002](./0002-inter-module-communication-via-events.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0028](./0028-diagnosis-questions-catalog-store.md), [ADR-0029](./0029-diagnosis-i18n-strategy.md), [diagnosis spec](../api/specs/02-diagnosis-recommendation.md), [US-2-7 시퀀스](../architecture/sequence-diagrams/02-diagnosis-recommendation/us-2-7-v2-server-driven-flow.md), [user-stories US-2-7](../requirements/user-stories.md), [issue #157](https://github.com/swyp-app-5th-team1/Kohere-backend/issues/157) |

## Status

Proposed

> 기존 v1 진단 질의응답([ADR-0028](./0028-diagnosis-questions-catalog-store.md): `GET /questions/{step}` + `POST /answers` + `POST /diagnoses`, 클라이언트가 `step`·확정 시점을 주도)은 그대로 둔다. 본 ADR은 issue #157의 요구(지역 매물 부재 시 재질의·종료, step 대신 서버 주도 next, 서버 자동 확정, 조정 제안 제거)를 **하위 호환이 깨지는 변경**으로 보고 `/api/v2`에 새 흐름으로 신설하는 결정이다.

## Context

- issue #157 재정의 요구: (1) ① 지역 답 직후 매칭 매물이 0건이면 "다른 지역 방을 찾아보시겠어요?"를 묻고 "예" → 지역부터 재시작 / "아니오" → **진단 챗봇 종료(종료코드 반환)**. (2) 클라이언트가 `step`을 지정하는 방식이 비효율적이라 **"next"만 요청하면 서버가 맞는 질문을 반환**하고, 언제 확정할지도 **서버가 판단**한다(빌더 완성 시 자동 확정, 지역 예외질문도 서버가 판단). (3) 추천 0건 시 조정 제안(`suggestions`)은 불필요 — 6단계까지 마친 뒤 매칭이 없으면 **`NO_MATCH` 코드만** 반환. (4) **기존 v1 로직은 건드리지 않고 API 버전을 올려** 구현한다.
- 배선 사실(코드 확인): `/api/v1`은 전역 설정(`context-path`/`servlet.path`/`WebMvcConfigurer` PathPrefix)이 아니라 **각 컨트롤러의 `@RequestMapping("/api/v1/...")` 리터럴**이다. `SecurityConfig`의 `.anyRequest().authenticated()`가 `/api/v2/**`를 자동으로 인증 대상에 포섭한다. 따라서 **새 컨트롤러 하나를 추가**하면 v1 파일·보안 설정 변경 없이 `/api/v2`가 붙는다([api-design-guide](../api/api-design-guide.md): "하위 호환이 깨지는 변경은 `/api/v2`로 올린다").
- v1 진단 도메인 사실(코드 확인): (a) 진행 중 답은 `diagnoses` 컬렉션의 IN_PROGRESS 초안(사용자당 1건)에 저장되고 `Diagnosis` 애그리거트에는 **진행 커서가 없다**. (b) `validateComplete`는 `region`·`purpose`·(목적 분기)`university`/`district`·`monthlyRentMin/Max`·`arcStatus`만 필수로 보고 **`conditions`(④)는 필수가 아니다**(비어도 통과). (c) ⑥ `arcStatus=NO_ARC`는 파생 조건 `NO_ARC`를 `conditions`에 교차 기록하고, 초안 시작 시 `conditions`가 빈 집합으로 초기화된다. → **"답 필드 null 여부"로 진행 위치·완료를 추론하는 것은 불안정**하다(④를 건너뛰거나 오판).
- 매칭 경로 사실: 지역-only 존재 확인과 최종 추천 모두 `listing`의 공개 query `recommendByCriteria`(동기)로 실현 가능하다. `diagnosis → listing::api` 의존은 이미 화이트리스트되어 있고, 즉시 결과가 필요한 조회는 이벤트가 아니라 동기 공개 query다([ADR-0002](./0002-inter-module-communication-via-events.md) Decision 5).
- 제약: v1의 추천 0건 응답(`suggestions`)과 그 시드(`diagnosisSuggestions`의 `NO_MATCH`)는 v1 코드가 사용 중이다 — 이를 전역 삭제하면 v1이 깨진다(요구4와 충돌). "종료코드/`NO_MATCH` 코드"는 에러가 아니라 정상 흐름 결과이므로 공통 에러 코드가 아니라 응답 `data` 안 결과값으로 표현해야 한다.

## Decision

**진단 질의응답의 서버 주도 흐름을 `/api/v2/diagnoses/next` 단일 대화형 엔드포인트로 신설하고, 진행 상태를 v1과 분리된 flow-session에 담으며, 매 응답을 정상 `200` `data` 안의 결과코드(태그드 유니온)로 표현한다. v1은 로직을 바꾸지 않는다.** 세부는 다음과 같다.

1. **`/api/v2`에 새 컨트롤러만 추가한다.** `DiagnosisV2Controller(@RequestMapping("/api/v2/diagnoses"))`의 `POST /next` 하나로 대화한다. 본문은 기존 `AnswerRequest`(현재 문항 답 1개, 최초·재개는 무답 허용). v1 컨트롤러·서비스·`SecurityConfig`·`package-info` 의존 화이트리스트는 변경하지 않는다.
2. **진행 위치·완료는 `cursor`가 단일 정본이다.** 정본 순서를 `DiagnosisFlowStep` enum으로 고정한다 — `REGION(1) → PURPOSE(2) → BRANCH(3: 저장된 purpose로 university|district) → CONDITIONS(4) → MONTHLY_RENT(5) → ARC_STATUS(6)`. `validateComplete`가 `conditions`를 필수로 보지 않고 `arcStatus` 파생이 `conditions`를 교차 기록하므로, 다음 질문·완료 판정을 필드 null이 아니라 **`cursor`(진행한 슬롯 수 0~6)** 로 강제한다(`validateComplete`는 자동 확정 시 정합 백스톱).
3. **v2 진행 상태는 별도 aggregate·컬렉션에 둔다.** `DiagnosisFlowSession { userId, draft(Diagnosis 누적 초안), cursor, state(IN_FLOW | AWAITING_REGION_RETRY) }`를 도메인 포트 `DiagnosisFlowSessionRepository`(구현은 infrastructure)로 **`diagnosisFlowSessions` 컬렉션**에 저장한다. v1의 `diagnoses`/IN_PROGRESS 초안을 공유하지 않는다 — (a) "사용자당 IN_PROGRESS 1건" 제약과 충돌 회피, (b) `cursor`·`state` 같은 절차 필드로 v1 `Diagnosis` 애그리거트를 오염시키지 않기 위해서다. **완료 시에만** `draft.complete()`로 정본 `Diagnosis`를 만들어 **기존 `diagnoses` 컬렉션에 저장**(v1 이력·상세·추천 읽기 경로 재사용)하고 세션은 삭제한다.
4. **매 응답은 정상 `200`의 결과코드(태그드 유니온)로 표현한다.** `DiagnosisFlowResponse { FlowResultCode resultCode, QuestionResponse question?, DiagnosisRecommendationView recommendation? }`, `FlowResultCode = NEXT_QUESTION | REGION_RETRY | COMPLETED | NO_MATCH | TERMINATED`(UPPER_SNAKE, null payload 필드는 직렬화 생략). 이는 에러가 아니므로 공통 에러 래퍼(`error`)나 `DiagnosisStatus`(IN_PROGRESS/COMPLETED, 도메인 전이 enum)에 넣지 않는다.
5. **① 지역 답 직후 조기 게이트 → 예=재시작 / 아니오=종료.** REGION을 방금 답해 `cursor`가 `0→1`이 되면, `region`만 채운 경량 `RecommendationCriteria`(`size=1`)로 `recommendByCriteria`를 동기 호출해 매칭 존재만 확인한다([ADR-0002](./0002-inter-module-communication-via-events.md) D5). 0건이면 `state=AWAITING_REGION_RETRY`로 전이하고 서버 합성 yes/no 질문("다른 지역 방을 찾아보시겠어요?")을 `REGION_RETRY`로 반환한다. 다음 호출에서 `field=regionRetry`·`code=YES`면 `region`만 비우고 `cursor=0`으로 재시작, `code=NO`면 세션을 삭제하고 `TERMINATED`(진단종료코드)를 반환한다. `regionRetry`는 서버 합성 제어 필드이며 진단 답 필드가 아니다(카탈로그·`Diagnosis`에 두지 않음).
6. **빌더 완성 시 서버가 자동 확정한다.** `cursor == 6`이면 `draft.complete(now)`로 확정(`IN_PROGRESS → COMPLETED`)·저장 후 전체 조건으로 `recommendByCriteria`를 호출해 매칭이 있으면 `COMPLETED`(+추천), 없으면 `NO_MATCH`(코드만)로 응답하고 세션을 삭제한다. 확정 시점을 클라이언트에 맡기지 않는다.
7. **v2는 조정 제안(`suggestions`)을 쓰지 않고 `NO_MATCH` 코드만 반환한다 — v1 자산은 그대로 둔다.** v1의 `suggestions` 6개 클래스·`RecommendationResponse.Suggestions`·`diagnosisSuggestions` 시드는 v1 추천 0건 경로가 사용 중이므로 **전역 삭제하지 않는다**(삭제하면 v1이 `500`·응답 계약 파괴 → 요구4와 v1 무변경(요구4·4)이 상호배타). v2는 그 기계를 **참조만 하지 않고** 0건이면 `FlowResultCode.NO_MATCH`만 반환한다(요구3의 취지 충족, 제안 문구는 클라 렌더).
8. **공유 로직은 헬퍼로 추출해 v1·v2가 공용한다.** `applyAnswer`(답 적용·파생 조건 주입)·`toCriteria`(조건 매핑)·문항 번역/③ 분기 계산을 공유 컴포넌트로 추출하고 v1 `DiagnosisService`는 이를 위임한다(동작 보존 리팩터, v1 통합·RestDocs 테스트가 회귀 가드). 분기·파생 규칙을 v2에 복제해 정합 버그 표면을 늘리지 않기 위해서다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| v1 엔드포인트를 서버 주도로 개조 | 엔드포인트 1벌 | 하위 호환 파괴, 요구4(v1 무변경) 위반, 클라 회귀 | 요구4가 명시적으로 버전 상향을 요구 |
| 다음 질문을 `draft` 필드 null로 추론(세션 없음) | 저장소 추가 없음 | `conditions` 비필수 + `arcStatus`→`conditions` 교차기록으로 진행 추론 불안정(④ 건너뜀·오판) | 완료·순서 판정이 정확하지 않음 |
| 진행 상태를 v1 `diagnoses`/IN_PROGRESS에 절차 필드 추가로 저장 | 컬렉션 안 늘림 | v1 `Diagnosis` 오염, "IN_PROGRESS 1건" 제약 충돌, v1 도메인 변경(요구4 위반) | v1 무변경·도메인 순수성 훼손 |
| 진행 상태를 Redis(TTL)에 저장 | 버려진 흐름 자동 만료, 세션성에 자연스러움 | 저장소 하나 더 관여, `diagnosis`는 Mongo 영속이라 일관성↓, 미완성 조회/관측 경로 별도 | Mongo 별도 컬렉션이 v1 초안 저장 패턴과 일관 — Redis는 후속 최적화로 열어둠 |
| 서버 stateless + 클라가 매 호출에 누적 답 전체 재전송 | 서버측 진행 상태 저장 불필요 | 서버 주도성 약화(순서·완료를 클라가 실어야 함), null 추론 모호성 재발, 신뢰 경계 확대 | 요구2(서버가 판단)와 배치 |
| suggestion 전역 삭제(시드 포함) | 요구3 문자 그대로 이행 | v1 추천 0건 경로 `500`·응답 계약 파괴(요구4 위반) | v1 무변경과 상호배타 — v1 은퇴 시에만 |
| 지역 존재확인용 `count`/`exists` 전용 포트 신설 | 정렬·뷰 매핑 낭비 제거, 의도 명확 | listing 포트 추가 작업 | `recommendByCriteria(size=1)` 재사용으로 즉시 착수, 오버헤드 시 후속 신설 |
| 결과코드 없이 `TERMINATED`/`NO_MATCH`를 에러 코드로 | 기존 에러 채널 재사용 | 정상 흐름을 에러로 표현(클라 분기 왜곡), `4xx`/`5xx` 오염 | 정상 결과는 `200` `data` 결과코드가 맞음 |

## Consequences

- 긍정: 클라이언트가 `step`·확정 시점·분기를 몰라도 되어 앱-서버 결합이 낮아진다. 지역 조기 차단으로 "0건인데 끝까지 진행" 비효율이 사라진다. v1은 그대로라 회귀 위험이 낮다. 진행 상태가 별도 컬렉션이라 v1 도메인이 오염되지 않는다. 결과코드가 정상 흐름이라 에러 채널이 깨끗하다.
- 부정/트레이드오프: 진단 모듈에 v2 전용 타입(컨트롤러·`DiagnosisFlowService`·`DiagnosisFlowSession`/포트/구현·결과 DTO·순서/상태 enum)이 늘어 표면적이 커진다. 공유 헬퍼 추출로 v1 `DiagnosisService` 파일이 바뀐다(동작 보존이지만 "문자 그대로 v1 무변경"은 아님 — 테스트로 가드). v2 완료 진단이 v1과 같은 `diagnoses` 컬렉션에 섞여 이력/추천 조회에 함께 나타난다(의도된 재사용). v1의 미사용 `suggestions` 자산이 남는다(데드코드 아님 — v1이 사용).
- 후속 작업: `diagnosisFlowSessions` `userId` 유니크 인덱스는 기동 시 부트스트랩 initializer(`DiagnosisFlowSessionIndexInitializer`)가 멱등 생성한다(인덱스=부트스트랩, 시드·데이터 진화만 Mongock — [migration-policy §8](../database/migration-policy.md)·[ADR-0032](./0032-mongodb-migration-runner.md)). 지역 존재확인 오버헤드가 크면 `listing::api`에 `count`/`exists` 포트 신설. v2 완료 진단 출처 구분이 필요해지면 태깅 검토. 스펙·US·시퀀스 문서 동반 갱신.

## Validation

- v2 흐름 테스트(순서 강제/자동 확정/지역 0건 → REGION_RETRY/예=재시작/아니오=TERMINATED/6단계 후 NO_MATCH)와 `DiagnosisV2Controller` RestDocs(결과코드별 nullable payload optional 문서화)로 검증한다. v1 통합·RestDocs 테스트가 공유 헬퍼 추출의 동작 보존을 가드한다.
- 지역 조기 게이트의 매칭 정의(`recommendByCriteria`의 PUBLISHED + `address.city` + ACTIVE roomOffer)가 최종 추천과 동일 필터의 상위집합인지 확인해 "게이트 통과인데 추천 0" 외의 불일치가 없도록 한다.
- 재검토 시점: v1 은퇴(그때 suggestion 전역 삭제·v2 승격 재검토), 또는 지역 존재확인 호출량이 성능 이슈가 될 때.
