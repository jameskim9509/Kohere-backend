# 진단 v2 마커 API 신설 + v1 진단 정리 — 구현 계획서 (일회성)

> 작성 2026-09-08 · 대상 브랜치 `develop` · **한 PR** · 작업 순서: **이관 → 문서 → 코드 → 테스트**
> 일회성 계획서다. 작업이 끝나면 지운다(커밋 대상 아님).

## 목차
- [0. 두 작업과 확정 결정](#0-두-작업과-확정-결정)
- [1. 마커 API 계약](#1-마커-api-계약)
- [Phase 0 — 이관 (반드시 선행)](#phase-0--이관-반드시-선행)
- [Phase 1 — 문서](#phase-1--문서)
- [Phase 2 — 코드](#phase-2--코드)
- [Phase 3 — 테스트](#phase-3--테스트)
- [4. 함정 체크리스트](#4-함정-체크리스트)
- [5. 커밋 분할](#5-커밋-분할)
- [6. 완료 판정](#6-완료-판정)
- [7. 범위 밖 / 후속](#7-범위-밖--후속)

---

## 0. 두 작업과 확정 결정

**작업 A — 마커 API 신설.** `GET /api/v2/diagnoses/{diagnosisId}/recommendations`는 페이지당 최대 100건이라
지도에 추천 매물 전체를 찍을 수 없다. 페이지 없이 마커만 주는 경로를 신설한다.

**작업 B — v1 진단 정리.** v1 7개 중 **v2에 대체가 있는 쓰기·추천 4개는 삭제**하고,
**대체가 없는 조회 3종은 URL을 그대로 두고 구현만 v2 기준으로 새로 만든다.**

| v1 경로 | 처리 |
| --- | --- |
| `POST /api/v1/diagnoses` (확정) | **삭제** — v2 `/next` 자동 확정이 대체 |
| `GET /api/v1/diagnoses/questions/{step}` (문항) | **삭제** — v2 `/start`·`/next` payload가 대체 |
| `POST /api/v1/diagnoses/answers` (답 저장) | **삭제** — v2 `/next`가 대체 |
| `GET /api/v1/diagnoses/{id}/recommendations` (추천) | **삭제** — v2-3이 대체 |
| `GET /api/v1/diagnoses` (이력) | **URL 유지 · 구현 신설** |
| `GET /api/v1/diagnoses/latest` (최근) | **URL 유지 · 구현 신설** |
| `GET /api/v1/diagnoses/{diagnosisId}` (상세) | **URL 유지 · 구현 신설** |

### 착수 전 반드시 알아야 할 사실 5개 (전부 실측)

**① `size`를 푸는 것만으로는 마커 API가 동작하지 않는다.**
```java
// ListingRepositoryImpl.java:50
private static final int MAX_PAGE_SIZE = 100;
// ListingRepositoryImpl.java:314  ← 예외 없음. size=100000을 실어도 100건만 온다.
int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
```
`recommend()`는 `:229`에서 `findPage`로 끝나고, `RecommendationCriteria` 생성자(`:47-58`)도 size를 검증하지 않는다.
응답 `page.size`도 100으로 나가 겉보기엔 정상이고, 시드가 100건 미만인 테스트는 전부 green이다.
→ **`findPage`를 거치지 않는 저장소 메서드 신설이 유일한 해법.**

**② 조회 3종은 "구현이 v1이라서" 문제인 게 아니다 — 이미 v2가 만든 회원 진단을 정확히 읽는다.**
v2 회원 흐름은 `DiagnosisFlowSession:51-57`이 `Diagnosis.startInProgress(userId)`로 시작하고
`DiagnosisFlowService:215-219`가 `draft.complete(now)`를 저장한다 → `userId`·`status=COMPLETED`·`submittedAt`이
모두 채워져 기존 질의에 그대로 걸린다. 답 적용은 v1과 **같은** `DiagnosisAnswerApplier:39-66`이고
`Diagnosis.validateComplete`(`:127-150`)가 6슬롯을 전부 필수로 강제하므로 **v2가 안 채우는 필드는 하나도 없다.**
→ **"v2 기준 신설"의 실질은 로직 재작성이 아니라 소유자·의존·시드·문서·테스트 교체다**(§2-5).

**③ 「소유권 규칙이 두 벌」은 사실이 아니었다 — 정정.**
`DiagnosisService.requireOwner:234-238`도 이미 `diagnosis.isOwnedBy(userId, null)`에 위임한다(실측).
차이는 게스트 키를 `null`로 고정한 것과 파라미터가 primitive `long`인 것뿐이다.
실제로 두 벌인 것은 **규칙을 감싼 wrapper 2개**와 **DISCARDED 게이트 2벌**
(`DiagnosisService:129-131` 인라인 `if` vs `DiagnosisRecommendationReader:75-79` 메서드)이다.
`validatePage`/`validateSort`는 두 클래스에 **바이트 단위로 동일**하게 있고 둘 다 리터럴 `100`을 쓴다.

**④ 삭제하면 안 되는 것이 원안에 셋 섞여 있었다.**
- `DiagnosisRepositoryImpl`의 「고아 import 제거」 → **즉시 컴파일 실패.** 조회 3종이 남기는 `:47-69`가
  `List`·`PageRequest`·`Sort`·`DiagnosisStatus`를 전부 계속 쓴다.
- `diagnoses.userId_submittedAt_idx` drop + 초기화기 삭제 → **이력·최근이 컬렉션 풀스캔 + in-memory sort로 조용히 떨어진다.**
  Boot 3.5는 자동 인덱스 생성이 꺼져 있어 이 러너가 유일한 생성 주체이고, 초기화기가 `@Profile("!test")`(`:22`)라
  **인덱스가 있는 상태로 도는 테스트가 0건** — drop해도 build가 완전히 green이다.
- `DiagnosisDocsTest` 통째 삭제 → **살아 있는 API 3개가 Swagger에서 증발하는데 CI가 통과한다.**
  `verifyOpenApiSpec`은 존재하는 오퍼레이션만 검사하고 「있어야 할 것이 없다」를 보지 않는다.

**⑤ legacy `conditions: ["NO_ARC"]` 문서가 조회 3종을 500으로 죽인다.**
`DiagnosisCondition`에 `NO_ARC` 상수가 **없다**(실측 — `MOVE_IN_NOW`…`NO_MAINT_FEE` 8개뿐인데 **클래스 javadoc은 아직 `NO_ARC`를 설명한다**).
`git log -S'NO_ARC'`로 확인: `47fe416`(#114)이 상수를 추가하며 order 0004 ChangeUnit이 `arcStatus=ARC_PENDING` 문서에
`$addToSet conditions:"NO_ARC"`로 백필했고, `188d9b8`(#222)이 상수를 제거했으며 **되돌린 마이그레이션이 0건**이다.
`DiagnosisDocument.conditions`는 `Set<DiagnosisCondition>`이라 미등록 문자열은 역직렬화에서 던진다.
**착수 전 `db.diagnoses.countDocuments({conditions:"NO_ARC"})`를 dev/prod에서 재라.**

### 작업 A 확정 결정

| # | 쟁점 | 결정 | 근거 / 기각한 대안 |
|---|---|---|---|
| A1 | 경로 | `GET /api/v2/diagnoses/{diagnosisId}/recommendations/map` | `/api/v2/listings/map`과 대칭. **기각: `?view=map`** — 같은 `(path, method)`를 공유해 200 스키마가 `(path,type)` dedup·last-wins로 조용히 증발한다(ADR-0017). 취향이 아니라 문서 생성기 제약 |
| A2 | 상한 | **500건**, 초과 시 **절단**(에러 아님) | `ListingService.MAX_MAP_MARKERS=500`과 같은 값·근거. **기각: 400** — 진단엔 bbox가 없어 클라가 좁힐 방법이 없다. **기각: 무제한** — 조건이 빈 진단에서 전 매물 스캔 |
| A3 | 응답 | `{ markers: [{listingId, lat, lng}], total }` | `ListingMapResponse`와 100% 동일. **기각: `truncated` 추가** — 두 map 응답 스키마가 갈린다 |
| A4 | 상태 게이트 | 마커 경로에만 `requireCompleted`(아니면 404), **소유권(403)보다 먼저** | 아래 「게이트 순서 불변식」 참조 |
| A5 | 좌표 없는 매물 | 공유 criteria에 `location != null` | `ListingResponseMapper:98-99`·`:152`가 무방비로 `getLocation().latitude()`를 부른다 → 좌표 null 문서 1건이면 **기존 페이지 조회도 이미 500**. 잠재 NPE 수정이지 새 정책이 아니다 |
| A6 | 정렬 | 파라미터 없음. `defaultSort()` + `_id ASC` 타이브레이커 | 절단이 있으면 동점 경계가 호출마다 흔들린다 |
| A7 | `resultCode` | 싣지 않는다 | 페이지가 없어 0건 모호성이 없다 |
| A8 | 언어 | 인자에서 제거 | 마커에 번역 대상 라벨이 0개 |
| A9 | 신규 ErrorCode | 만들지 않는다 | A2에서 절단을 택했다 |
| A10 | 보안 매처 | **변경 0건** | `SecurityConfig:246-247`이 메서드 무제한 `**`이고 위 매처 중 `/api/v2/diagnoses` 접두사가 0건 |
| A11 | Mongo 프로젝션 | **범위 밖** | `ListingMongoMapper.toDomain:52`가 `getRoomOffers().stream()`을 null 방어 없이 부른다 |
| A12 | `RecommendationCriteria` 분해 | 하지 않는다 | 생성 15곳 + 단정 11곳을 동시에 깬다. 저장소 포트에만 조건 VO 도입(C4) |

**게이트 순서 불변식 (A4 · B6 공통)** — 상태 게이트(404)를 소유권(403)보다 **반드시 먼저** 호출한다.
뒤집으면 타인의 폐기·미확정 진단이 404가 아니라 403이 되어, 전역 순차 채번 id 위에 **존재 신호**가 새로 생긴다.

### 작업 B 확정 결정

| # | 쟁점 | 결정 | 근거 |
|---|---|---|---|
| B1 | 범위 | 쓰기·추천 **4개 삭제** / 조회 **3종 URL 유지 + 구현 신설** | v2에 대체가 있는 것만 지운다 |
| B2 | 게스트 | **열지 않는다.** `SecurityConfig` 0줄 변경 | 결정적 근거는 보안이 아니라 **의미론적 공허함**이다 — `DiagnosisFlowService:100-103`이 `/start`마다 키를 새로 발급하고 `/start`는 `X-Guest-Session-Id`를 받지도 않으며(`DiagnosisV2Controller:55-59`), 확정(`:216`)·폐기(`:180`)가 세션을 지운다 → **한 게스트 키에 달리는 종료 진단은 최대 1건**(spec 02:103이 이미 명문화). 이력은 0~1건이고 `/latest`·`/{id}`는 그 1건과 같으니, 세 경로가 클라가 이미 쥔 값 위에 얹는 정보량이 **정확히 0**이다. 여는 비용은 파생쿼리 3 + 포트 3 + 구현 3 + 인덱스 1 + `long→Long` 3 + 확정 결정 4곳 반전(spec 02:55·:63·:68 · user-stories:1121) |
| B3 | 응답 계약 | **1바이트도 바꾸지 않는다** | 그것이 "v1 URL 유지"의 실질이다. `DiagnosisResponse`·`LatestDiagnosisResponse` 필드·순서·타입 무변경, RestDocs identifier 13개 무변경 |
| B4 | `getDetail` 게이트 | `DISCARDED`만 404 → **`COMPLETED` 아니면 404**로 승격 | 이 PR의 **유일한 관측 가능 동작 변경**(legacy `IN_PROGRESS` 상세 200→404). 안전성 근거: `AnswerSavedResponse`가 `record(boolean saved)` 하나뿐이라 **`IN_PROGRESS` id를 클라에 발급한 서버 표면이 0**이었다 |
| B5 | 레코드 이관 | `RecommendationResponse` 삭제, 중첩 3개 이관 | 전치 위험은 `DiagnosisV2DocsTest:317-336`이 11+3 필드 **전부에 서로 다른 값**으로 단정해 잡는다(코드 주석 `:314-316`이 목적을 명시) |
| B6 | 게이트·검증 사본 | `DiagnosisAccessGuard`·`DiagnosisPageRequests` 한 벌로 정렬 | 사실 ③. **소유권 규칙 자체는 이미 한 벌**이므로 새로 통일할 것은 wrapper와 DISCARDED 게이트, 그리고 바이트 단위로 같은 page/sort 검증이다 |
| B7 | operationId | **개명하지 않는다** | v1 조회 3개가 남아 접두사 상황이 그대로다. ADR-0017 `:42`의 목적(발행된 Examples 키 불변)은 더 강하게 유효 |
| B8 | 인덱스 | `userId_submittedAt_idx` **존치** | 사실 ④. 죽은 `diagnosisQuestions.active_step_idx`만 제거 |
| B9 | legacy `NO_ARC` | **`diagnoses` 문서 전량 삭제**(ChangeUnit). 심층 방어 없음 | 운영 실측 **40건**. **사실 ⑤는 틀렸다 — 실측하니 미등록 enum 원소는 예외가 아니라 조용히 버려진다**(응답에서 빠질 뿐 500이 아니다). 따라서 이 삭제는 장애 수습이 아니라 **위생 작업**이며, 근거는 「시험 데이터라 지워도 된다」이다. 읽기 쪽 방어는 불필요하고, 조용히 버려지는 성질만 회귀 테스트로 고정한다 |
| B10 | 규약 | api-design-guide §2-1에 **선별 제거** 패턴을 명문화 | 진단 v1은 「전면 스텁」(매물 v1)도 「완전 삭제」도 아닌 **세 번째 패턴**(경로별 선별 제거 + 잔존 경로는 실데이터)이다. 문안에 **「잔존 경로에는 `deprecated`를 붙이지 않는다」**를 반드시 넣어야 `:65`와 충돌하지 않는다 |

---

## 1. 마커 API 계약

```
GET /api/v2/diagnoses/{diagnosisId}/recommendations/map
```
**Headers** — `Authorization: Bearer <accessToken>`(선택) · `X-Guest-Session-Id`(게스트 필수) · **Query 없음**

```jsonc
{ "success": true,
  "data": { "markers": [ { "listingId": "68b1f0c2a4d3e10012a4b7c1", "lat": 37.5665, "lng": 126.9780 } ],
            "total": 137 },
  "error": null }
// 0건: { "markers": [], "total": 0 } — 에러가 아니다
```

| status | `error.code` | 발생 조건 |
|---|---|---|
| 401 | `TOKEN_EXPIRED` | 토큰 만료. 미전송·위조는 게스트로 처리하므로 `UNAUTHENTICATED`는 발생하지 않는다 |
| 403 | `FORBIDDEN` | 타인 소유, 게스트↔회원 교차(양방향), 신원 없는 요청 |
| 404 | `DIAGNOSIS_NOT_FOUND` | 미존재 · `DISCARDED` · **미확정** |

> `INVALID_INPUT`(400)은 발생하지 않는다(파라미터가 없다). 다만 `{diagnosisId}`에 비숫자가 오면
> 공통 `MALFORMED_REQUEST`(400)다(`GlobalExceptionHandler:117-124`). "400은 발생하지 않는다"라고 쓰지 말 것.

**`total`** — 조건에 맞는 전체 매물 수(절단 전). `markers`는 최대 500건. `markers.length < total`이면 절단.
`count`와 `find`가 별개 쿼리라 경계에서 1~2건 오차가 날 수 있는 **참고값**임을 스펙에 남긴다.

---

## Phase 0 — 이관 (반드시 선행)

> 삭제하면 사라질 자산을 먼저 옮긴다. 이 Phase는 단독 `build` green이어야 하고 삭제는 하나도 하지 않는다.

### 0-1. 문서 계약 이관 — `docs/api/specs/02-diagnosis-recommendation.md`

| 원본 | 목적지 | 왜 |
|---|---|---|
| `:511` `RecommendationCriteria` 매핑 계약 | v2-3 (`:770-834`) | v2-3 리드가 「§7과 계약이 같다」로만 규정 |
| `:527` 정렬 구현 제약(`price,desc` 미반영 · `distance` 미구현) | v2-3 | **A6의 근거가 이 문단이다** |
| `:529-567` `content[]` 200 예시 | v2-3 (`:805` 플레이스홀더를 실제 예시로) | `:805`가 `"content": [ /* §7과 동일 */ ]`라 §7을 먼저 지우면 **v2 계약이 문서에서 증발** |
| `:569` ADR-0037 표시 언어 | v2-3 | |
| `:187-212` §1 문항 응답 형태 | v2-1 (`:652-700`) | v2-1 `:645`·`:691`이 「§1과 동일」로만 규정 |
| `:236-266` §2 `AnswerRequest` 3형태 | v2-2 (`:701-769`) | **ADR-0036 `:105`가 세 형태 예시 보존을 요구** |
| `:314-322` §3 검증 표 | 「진단 입력 enum 정의」(`:115-141`) 또는 v2-2 | v2 `validateComplete`가 그대로 쓴다 |

> **§4 이력(`:354-411`)·§5 최근(`:413-459`)·§6 상세(`:461-503`)는 이관하지 않는다 — 존치·개정 대상이다.**
> 이 세 절은 §1~§3·§7을 참조하지 않아 절개가 깨끗하다(실측).

### 0-2. 유저 스토리 AC 이관 — `docs/requirements/user-stories.md`

| 원본 | 목적지 |
|---|---|
| US-2-1 `:1025-1029`(conditions 4개 이상) · `:1041-1045`(월세 0/0·음수·min>max) | US-2-7 (`validateComplete` 경계 AC인데 US-2-7 `:1352-1356`에 대응물이 없다) |
| US-2-2 `:1084-1088`(페이지 파라미터 400 AC) | v2 기준 개정한 US-2-2 |
| US-2-4 「덮어쓰지 않고 새 레코드」 불변식 | US-2-7 |
| **US-2-5 `:1184`(선택지 코드 ↔ 제출 enum 1:1 일치)** | US-2-7 |
| **US-2-5 `:1189`(③ 입국 목적 분기 + 대학 그룹 6개 코드·라벨 전체)** | US-2-7 |
| **US-2-5 ⑤ 월세는 고정 선택지가 아닌 `NUMBER_RANGE` 예외** | US-2-7 |
| **US-2-6 `:1233`(미지원 언어 폴백 `ja`→`en`)** | US-2-7 |
| **US-2-6 `:1238`(번역과 무관하게 코드로 제출 검증)** | US-2-7 |

> **US-2-5·US-2-6은 US-2-7로 흡수 후 삭제한다**(§1-1). 위 5개만 옮기고 나머지 AC는 버린다 —
> US-2-5의 「`/start`로 첫 질문 수령」(`:1174`)·「답 저장 후 다음 질문」(`:1179`)·「완료 후 제출」(`:1194`)을
> v2로 고쳐 쓰면 US-2-7 `:1261`·`:1271`·`:1291`과 **같은 문장이 되어 계약이 두 곳에 생긴다.**
> **US-2-2·US-2-3은 이관·삭제 대상이 아니다 — 존치·개정한다.**

### 0-3. 테스트 커버리지 이관 — `DiagnosisMongoIntegrationTest` → `DiagnosisFlowServiceIntegrationTest`

**`DiagnosisFlowServiceIntegrationTest`에 `RecommendationCriteria`·`ArgumentCaptor` 참조가 0건임을 실측 확인.**
이관 없이 지우면 작업 A가 요구하는 "기존 추천과 완전히 동일한 매칭 조건"의 회귀 가드가 0이 된다.
**조회 3종 존치와 무관하게 범위가 줄지 않는다** — 아래는 전부 v1 쓰기 경로 구동이다.

| 원본 | 지키는 공유 컴포넌트 |
|---|---|
| `:351` · `:416` · `:449` | `DiagnosisCriteriaMapper` 대학그룹 전개·ETC 여집합·월세 상하한·sort 기본값·arcStatus 전달 (`ArgumentCaptor<RecommendationCriteria>`) |
| `:262` `answerValidation` · `:469` · `:499` | `DiagnosisAnswerApplier` — enum 6종 파싱·조건 최대 3개·월세 min≤max·`NO_ARC` 직접선택 거부 |
| `:183` `questionTranslation` | `DiagnosisQuestionTranslator` ko + **미지원 `ja`→`en` 폴백** — v2 테스트 `setUp:101`이 `getLanguage`를 항상 `en`으로 스텁해 폴백을 못 탄다 |
| `:146` `sequentialIdAndRediagnosis` | `SequenceGenerator` 순차 채번 |

**추가**: `discardedIsInvisibleToV1InProgressLookup`(`:245-256`)의 `:253-255`를
`diagnosisMongoRepository.findAll()` + status 필터로 다시 쓰고 이름의 `v1`을 걷어낸다.
**이래야 `DiagnosisMongoRepository:12`를 지울 수 있다**(유일한 잔존 사용처).

---

## Phase 1 — 문서

### 1-1. v1 문서 정리 (0-1·0-2 이관 완료 후)

**`docs/api/specs/02-diagnosis-recommendation.md`**
- 삭제: §1(`:163-221`) · §2(`:223-290`) · §3(`:292-352`) · §7(`:505-613`) — **약 300줄**(원안의 453줄이 아니다)
- **존치·개정**: §4(`:354-411`) · §5(`:413-459`) · §6(`:461-503`). `## 상세` 헤딩(`:161`)도 살아남으므로
  **「v2가 유일 상세 절이 되어 heading 재편」 전제는 무효**
- 요약 표: `:145-153`에서 **4행만** 삭제(문항·답변·확정·추천). 조회 3행 존치. `:157`의 「v1 7개」 → 「v1 조회 3종」
- 「### v1은 회원 전용」(`:61-77`)은 **삭제가 아니라 개정** — `:68`이 조회 3종의 게스트 정책 정본이다
- §6에 상태 게이트 개정 반영(「폐기 기록·**미확정 진단**은 404」 — B4)
- `:626`(「완료 시에만 정본 진단을 만들어 기존 `diagnoses`에 저장 — v1 이력/상세 조회 재사용」)은 **이제 참으로 남는다** → 소폭 문구 조정만
- `:95`의 「소유권 검사가 도는 지점(§6·§7·v2-3)」에서 §7만 뺀다
- 참조 수리: enum 절 `:124`의 dangling `(§7)`
- **절 번호는 §4·§5·§6 그대로 둔다** — §1~§3으로 재번호하면 파일 내 §참조 28줄 + 외부 10곳
  (`DiagnosisFlowService:147` · `DiagnosisRecommendationReader:32` · `RecommendationResultCode:7` ·
  `DiagnosisV2Controller:78` · `DiagnosisDocsFields:42`·`:272`·`:327`·`:859`·`:907` · `http/diagnosis-v2.http:199`)이 검토 대상이 된다

**`docs/requirements/user-stories.md`**
- 삭제: US-2-1(`:1008-1045`) · US-2-4(`:1133-1160`) · **US-2-5(`:1162-1214`) · US-2-6(`:1216-1247`)**
  — 넷 다 Phase 0-2에서 고유 AC를 US-2-7로 옮긴 뒤에만.
  **US-2-5·US-2-6 흡수 근거**: 둘 다 삭제되는 v1 두 엔드포인트(`GET /questions/{step}`·`POST /answers`) 전용 스토리이고,
  역량 자체(서버가 문항을 단계별로 제공 · 표시 언어 번역)는 **v2 흐름이 그대로 수행하므로 US-2-7이 이미 서술한다.**
  v2로 고쳐 쓰면 US-2-5 `:1174`·`:1179`·`:1194`가 US-2-7 `:1261`·`:1271`·`:1291`과 **같은 문장이 되어
  같은 계약이 두 곳에 생기고 나중에 한쪽만 고쳐진다.** 고유 AC 5개만 옮기고 스토리는 지운다
- **US-2-3(`:1090-1131`) 존치·개정** — 정확히 조회 3종의 스토리이고 AC 5개가 세 경로를 직접 호출한다.
  `:1092` As a의 회원 전용 근거를 「v1이라서」 → 「`SecurityConfig`에 매처를 두지 않아서」로,
  `:1098`에 「읽는 대상은 v2 흐름이 확정한 진단」을 명시, 상세 AC에 **`IN_PROGRESS` 404** 추가.
  **`:1121`(「게스트용 이력·최근 시맨틱을 새로 정의하지 않는다」)은 주어만 「v1 조회 3종」으로 좁혀 유지** — B2의 근거 문장이다
- **개정(삭제 금지)**: **US-2-2**(추천 조회) — v1 §7이 아니라 **v2-3 + 신설 v2-4(마커)** 기준으로 다시 쓴다.
  US-2-5·US-2-6과 달리 「추천 결과 조회」는 흐름이 아니라 **별개 엔드포인트 계열**이라 US-2-7과 중복되지 않는다
  (US-2-7 `:1318`은 「클라가 시점을 정한다」만 말하고 필터·매핑·페이지·정렬·마커 계약은 안 다룬다).
  **작업 A의 마커 AC도 여기 붙인다**(US-2-7이 아니라)
- ADR-0028·ADR-0029의 「관련 유저 스토리」 참조를 **US-2-7로 갱신** — 흡수로 근거 스토리가 옮겨간 것이지 사라진 것이 아니다
- US-2-7 재작성: `:1257` · `:1295` · `:1307` · `:1322` · `:1357-1362`
- `:1006` — **1-3의 「셋→넷」 편집과 같은 문장에서 만난다. 한 번에 처리한다**

**시퀀스 다이어그램** — 스토리 1:1을 지키는 것이 배치 기준이다(CLAUDE.md 「스펙·스토리·다이어그램·코드 1:1」)

| 파일 | 처리 | 근거 |
|---|---|---|
| `us-2-1-submit-diagnosis.md` | **삭제** | US-2-1 삭제 |
| `us-2-4-rediagnosis.md` | **삭제** | US-2-4 삭제 |
| `us-2-5-2-6-diagnosis-questions.md` | **삭제** | US-2-5·US-2-6이 US-2-7로 흡수됨 |
| `us-2-2-recommendations.md` | **존치·개정** | US-2-2가 살아남으므로 다이어그램도 남아야 1:1이 지켜진다. v1 §7 경로를 **v2-3으로 다시 그리고 `④ 추천 조회` 뒤에 마커(v2-4) 분기를 추가** — 작업 A의 다이어그램 자리가 여기다 |
| `us-2-3-diagnosis-history.md` | **존치·개정** | 조회 3종. 본문 URL 3개(`:13-45`) 무변경, `:49-53`만(`:52`의 v1 7개 전제, `:53` 미래형) |
| `us-2-7-v2-server-driven-flow.md` | **존치·개정** | 아래 별도 항목 + 흡수 AC 5개 반영 |

- `02-.../README.md`: 표에서 **US-2-1 · US-2-4 · US-2-5·US-2-6 세 행 제거**. US-2-2·US-2-3·US-2-7 행은 존치하고
  US-2-2 제목을 v2 기준으로(「진단 결과(추천 매물 + 지도 마커) 조회」). 도입 인용문 `:5`(「아래 v1 다이어그램(us-2-1 ~ us-2-6)에는
  게스트 분기가 없다」)는 대상 파일이 사라지므로 재작성
- 개수: 02 폴더 `6`→**`3`**, 총계 `53`→**`50`**. `sequence-diagrams/README.md:31`·`:39` · `docs/index.md:63`
- **최상위 `README.md`에는 다이어그램 개수 표기가 없다 — 손대지 않는다**

**`us-2-7-v2-server-driven-flow.md`** — **참여자·화살표·호출 순서는 하나도 바뀌지 않는다**(작업 B는 v2 흐름 코드를 안 건드린다).
바뀌는 것은 텍스트 **14곳**이고, mermaid 블록이 `:7-202`이므로 그중 **4곳이 다이어그램 안(Note)**이다.

| 위치 | 안/밖 | 왜 바뀌나 |
|---|---|---|
| `:24` | **Note** | 「v1 진단은 회원 전용으로 남는다」 → 조회 3종만 남으므로 표기 축소 |
| `:159` | **Note** | 「v1과 동일 저장/이력 경로 재사용」 → 그 이력 경로가 이제 **유일한** 경로 |
| `:174` | **Note** | 「**v1 §7과 같은 조회지만** suggestions가 없다」 → §7이 삭제돼 **계약을 직접 서술** |
| `:187` | **Note** | 「v1은 회원 전용이라 게스트 분기를 타는 건 v2뿐」 → 그대로 참, 소폭만 |
| `:5` | 밖 | 범위 문단의 「기존 v1은 그대로 두고」 |
| `:206`·`:209`·`:210` | 밖 | 「v1은 그대로 유지된다」·v1 IN_PROGRESS 초안 비교·「v1 흐름 무오염」 |
| **`:211`** | 밖 | **`ofStep(step)`을 삭제하므로 「v1도 클라가 지정한 step의 field를 이 매핑으로 정한다」가 통째로 무효** |
| `:217`·`:221` | 밖 | v1 `diagnosisSuggestions` 참조 · v1 확정 시점 대비 |
| **`:222`·`:223`** | 밖 | **「v1 §7(US-2-2)과 계약이 같고」로 v1을 계약 근거로 삼는다 — §7이 사라지므로 자기완결적으로 재작성** |
| `:230` | 밖 | 「셋으로 닫히며」 → 「넷」(작업 A) + v1 7개 표기 |

> `:222`·`:223`이 가리키는 US-2-2는 **살아남는다**(제목만 v2 기준으로 바뀐다) — 링크는 유지하고 §7 참조만 걷어낸다.
> **마커 분기는 여기 그리지 않는다**(1-3 — `us-2-2-recommendations.md`가 자리다).

**그 밖** — `docs/api/specs/README.md:5`(종수) · `03-listings-favorites.md:95`(「양쪽」이 거짓이 된다) ·
`domain-model.md`(`:27`·`:562-566`·`:605` 삭제, `:531`·`:544` 재작성, **`:635`**「매물은 클라가 v1 추천으로 별도 조회」 정정) ·
`database-design.md`(`:19`·`:725-735`·`:722`·`:723` 삭제, `:680`·`:685`·`:695`·`:720`·`:1212`·`:1213` 재작성) ·
`migration-policy.md:117` · `system-overview.md:136`·`:387` · `us-1-15-landlord-account-merge.md:121` ·
`README-redesign-plan.md:540`(미추적 — 범위 밖이면 PR 본문에 명시)
> **`error-response-guide.md`는 무변경 가능** — `:106`·`:110`·`:112`는 회원 전용 유지 시 개수를 적지 않아 그대로 참이다.

### 1-2. ADR — 본문 수정 금지, 배너만

| ADR | 배너 |
|---|---|
| 0036 | `:110`이 「재검토 시점: v1 은퇴」를 예고했다 — 결정 11(`:49` suggestions 자산 보존) 폐기. **회귀 가드로 인용된 테스트가 바뀌므로 대체 가드를 명시** |
| 0028 | 문항 제공 경로가 v2 `/start`·`/next` payload로, 선정 주체가 클라 step → 서버 `pendingField`로 이동. **「관련 유저 스토리」 참조를 US-2-5 → US-2-7로** |
| 0029 | 3번째 배너 — 번역 대상에서 조정 제안이 빠져 결정 8 후반부 무효. **「관련 유저 스토리」 참조를 US-2-6 → US-2-7로**(번역 전략 자체는 v2·퀴즈·생활팁이 계속 쓰므로 유효) |
| 0038 | **Proposed라 본문 직접 수정** — `:118` `POST /api/v1/diagnoses` → `POST /api/v2/diagnoses/next` |
| **0017** | `:42`에 **「종료된 버전을 삭제하더라도 정본 identifier는 개명하지 않는다.」** 추가 (B7) |

### 1-3. 마커 API 문서 (1-1 완료 후 라인 재측정)

**`api-design-guide.md` — 이 PR에서 세 곳**
1. §2 「중첩 1단계」에 **표현(view) 접미사는 중첩으로 세지 않는다** 예외 + `/listings/map`·`/recommendations/map` 사례 (A1)
2. §2-1 `:55-65`에 **선별 제거 패턴** 명문화 (B10) — 「출시된 클라이언트가 쓰지 않음이 확인된 경로는 개별 제거할 수 있고,
   **잔존 경로는 실데이터를 계속 반환하며 `deprecated`를 붙이지 않는다**」. 진단 v1을 첫 사례로
3. `:46` 버전 정책 표 진단 행의 v1 열 → **「조회 3종」**(「없음」이 아니다)

**02 스펙에 `### v2-4.` 신설** — v2-3 구조 미러링. 본문 필수:
확정 진단만 조회 가능(A4) · 상한 500·절단·`total` 정의·경계 오차(A2/§1) ·
정렬을 받지 않으며 절단 집합은 추천 기본 정렬 상위(A6, 근거는 0-1로 이관된 `:527`) ·
경로를 `/map`으로 가른 근거를 **「문서 생성기 제약」**으로(A1).
요약 표에 1행, 게스트 헤더 요구 표(`:49-53`)에 1행.
**v2 종수 「3개」 표기 8곳은 1-1에서 v2 기준 재작성하며 「4개」로 맞춘다(두 번 고치지 않는다).**

**마커 API의 스토리·다이어그램 자리**
- AC는 **v2 기준으로 개정한 US-2-2**에 붙인다(US-2-7이 아니다 — US-2-7은 흐름, US-2-2는 추천 조회 계열이다).
  최소 3개: ① 정상 — 페이지 없이 전체 마커 + `total` ② 상한 초과 — 500건 절단이고 에러가 아니며 `markers.length < total`
  ③ 미확정 진단 → 404
- 다이어그램은 **`us-2-2-recommendations.md`의 `④ 추천 조회` 뒤 분기**로 그린다(1-1 표)
- `us-2-7`은 `:230`의 「셋으로 닫히며」만 「넷」으로 고친다 — 마커 분기를 여기 그리지 않는다(중복 방지)

---

## Phase 2 — 코드

### 2-1. 레코드 이관 → `RecommendationResponse` 삭제 (순수 cut/paste)

| 이동 | 목적지 |
|---|---|
| `MapMarker`(`:48`) | **신규 top-level** `diagnosis/application/dto/RecommendationMapMarker.java` |
| `RecommendedListing`(`:31-42`) · `CodeLabel`(`:45`) | `V2RecommendationResponse` 중첩으로 |
| `Suggestions`(`:51`) · `SuggestionAction`(`:54`) · outer record | **삭제** |

```java
public record RecommendationMapMarker(String listingId, double lat, double lng) {}
```
> 레코드 타입명은 Jackson 직렬화에 나타나지 않는다 — **JSON·RestDocs 기술자·OpenAPI 스키마 전부 무변경.**
> `V2RecommendationResponse`의 `markers` 타입 교체 + 참조 6곳(`:27`,`:28`,`:37`,`:40`,`:51`,`:55/:57`).

### 2-2. v1 쓰기·추천 삭제

**통째 삭제 (9파일)**
```
diagnosis/application/dto/DiagnosisCreatedResponse.java
diagnosis/application/dto/AnswerSavedResponse.java      ← record(boolean saved) 하나뿐 — B4 안전성 근거
diagnosis/application/dto/RecommendationResponse.java   ← 2-1 이관 후
diagnosis/application/SuggestionMessages.java
diagnosis/domain/SuggestionCatalog.java
diagnosis/domain/SuggestionContent.java
diagnosis/infrastructure/SuggestionCatalogImpl.java
diagnosis/infrastructure/DiagnosisSuggestionDocument.java
diagnosis/infrastructure/DiagnosisSuggestionMongoRepository.java
src/test/resources/fixtures/diagnosis-suggestions.json
```

**부분 절개**
- `DiagnosisController`: 매핑 **4개만** 삭제(`submit :43-49` · `getQuestion :51-55` · `submitAnswer :57-61` · `getRecommendations :84-93`).
  **파일·클래스·`@RequestMapping("/api/v1/diagnoses")`(`:37`)는 존치** — 유지할 3개 URL의 유일한 소유자다.
  고아 import **9개** 제거(`AnswerSavedResponse:7`·`DiagnosisCreatedResponse:8`·`QuestionResponse:11`·`RecommendationResponse:12`·`AnswerRequest:13`·`java.net.URI:14`·`ResponseEntity:16`·`PostMapping:20`·`RequestBody:21`) 직후 `spotlessApply`
- `DiagnosisRepository`: **`:20 findInProgressByUserId` 이 한 줄만.** `import java.util.List`(`:3`)는 `:26`이 쓰므로 존치
- `DiagnosisRepositoryImpl`: **`:40-45` 이 6줄만.** `List`·`PageRequest`·`Sort`·`DiagnosisStatus`·`Optional` import는 `:47-69`가 계속 쓴다 — **원안의 「고아 import 제거」를 그대로 하면 컴파일 실패**
- `DiagnosisMongoRepository`: **`:12`만.** `:14`·`:17`·`:20`과 `Page`·`Pageable` import는 존치. **Phase 0-3 완료 후에만**
- `DiagnosisIndexInitializer`: **`:36-40`(`diagnosisQuestions active_step_idx`)만.** `:31-35` 존치, **파일 삭제 금지**(B8).
  javadoc `:17`의 `(active, step)` 절만 제거
- `DiagnosisFlowStep`: `ofStep(int)`(`:74-81`) 삭제 — main 유일 호출자가 `DiagnosisService:67`
- `SecurityConfig`: **매처 무변경.** `:244-245` 주석을 **삭제하지 말고** 경로 표기만 좁힌다 —
  「v1 진단 조회 3종(`GET /api/v1/diagnoses` · `/latest` · `/{diagnosisId}`)은 매처를 추가하지 않고
  `anyRequest().authenticated()`에 남겨 회원 전용으로 유지한다」. **이 문장이 「매처 없음」이 사고가 아니라 결정임을 남기는 유일한 기록이다**
- `DiagnosisDocument`: **인덱스 선언 존치**(B8). javadoc `:26`·`:28-31`(게스트 문서가 userId 질의에 안 걸린다)은 **그대로 참이라 무변경** — B2의 코드상 근거이기도 하다
- `package-info.java`: javadoc `:2-3`·`:9`만. **`allowedDependencies`(`:12-14`)는 절대 건드리지 않는다**

**javadoc만** — `DiagnosisRecommendationReader`(`:18`·**`:22`**·`:33`·`:47`·`:83`의 dangling `{@link DiagnosisService...}` 2건 포함.
**`:25-26`은 회원 전용 유지 시 그대로 참이므로 존치**) · `DiagnosisFlowService:39` · `DiagnosisV2Controller:23-24`·`:27-28` ·
`DiagnosisCriteriaMapper` · `DiagnosisAnswerApplier:17-19` · `DiagnosisQuestionTranslator:12-13` · `QuestionResponse:6-11` ·
`presentation/dto/AnswerRequest`(**패키지 이동 금지** — `DiagnosisV2Controller:71`이 `@RequestBody`로 받는다) ·
`listing/api/ListingRecommendationService:12` · `RecommendationCriteria:19` · `listing/api/package-info:8` ·
**`ArcStatus:4`·`DiagnosisCondition:4-6`의 dangling `{@link DiagnosisCondition#NO_ARC}`**(사실 ⑤)
> `build.gradle`에 javadoc/doclint 설정이 0건이라 dangling `{@link}`를 잡는 게이트가 없다. 리뷰 체크리스트로 강제.

### 2-3. 마커 API 코드 (listing → diagnosis)

**C1. `listing/api/RecommendedListingMarkersView.java` (신규, public)**
```java
public record RecommendedListingMarkersView(List<Marker> markers, long total) {
  public RecommendedListingMarkersView { markers = List.copyOf(markers); }
  public record Marker(String listingId, double lat, double lng) {}
}
```
> **반드시 `com.kohere.listing.api`에.** `ListingMapResponse`(`listing.application.dto`)나
> `ListingMapSearchResult`(`listing.domain`)를 diagnosis가 import하면 `ModularityTest`가 깨진다.

**C2.** `ListingRecommendationService`에 `RecommendedListingMarkersView recommendMarkersByCriteria(RecommendationCriteria)` 추가
(`limit`은 노출하지 않는다 — 상한은 listing의 정책. `ListingService.getListingMap`과 같은 구조)

**C3.** `listing/domain/ListingRecommendationCondition.java` (신규) — 매칭 조건 8개 VO

**C4.** `ListingRepository`
```java
PageResponse<Listing> recommend(ListingRecommendationCondition condition, int page, int size, String sort);
ListingMapSearchResult recommendForMap(ListingRecommendationCondition condition, int limit);
```
> `recommend(...)` 호출자는 `ListingRecommendationServiceImpl:47` **한 곳뿐**이고 `ListingMongoIntegrationTest`는
> `recommendByCriteria(RecommendationCriteria)`를 거치므로 14개 생성 지점은 손대지 않는다.

**C5. `ListingRepositoryImpl`** — criteria 조립부(`:191-228`)를 `private static Criteria recommendCriteria(...)`로 **추출**.
**두 메서드가 이 한 벌을 공유하는 것이 "동일 조건 매칭"의 유일한 보장이다.** 추출한 criteria에 `location != null` 추가(A5).
```java
@Override
public ListingMapSearchResult recommendForMap(ListingRecommendationCondition condition, int limit) {
  int safeLimit = Math.max(1, limit);
  Criteria criteria = recommendCriteria(condition);
  long totalElements = mongoTemplate.count(new Query(criteria), ListingDocument.class);
  // searchForMap(:153-156)의 조기 반환을 복사하지 말 것 — 초과 시 빈 배열이 아니라 상한까지 잘라 준다.
  Query query = new Query(criteria).with(markerSort()).limit(safeLimit);
  List<Listing> content = mongoTemplate.find(query, ListingDocument.class).stream()
      .map(ListingMongoMapper::toDomain).toList();
  return new ListingMapSearchResult(content, totalElements);
}
/** 절단이 있으므로 동점 경계가 호출마다 흔들리지 않게 유니크 타이브레이커를 붙인다. */
private static Sort markerSort() { return defaultSort().and(Sort.by(Sort.Direction.ASC, "_id")); }
```

**C6. `ListingRecommendationServiceImpl`** — `MAX_RECOMMENDATION_MARKERS = 500`,
축 순서는 `ListingResponseMapper.toMapMarker:150-153`과 동일하게 `.latitude(), .longitude()`.
기존 `recommendByCriteria`도 `toCondition(criteria)`를 쓰게 고친다. 로컬라이제이션 컨텍스트를 만들지 않는다(A8).

**C7. `V2RecommendationMapResponse`** (신규) — `List<RecommendationMapMarker> markers, long total`.
2-1의 top-level 타입을 써서 두 v2 응답의 마커 모양을 타입으로 묶는다.

**C8. `DiagnosisRecommendationReader`**
```java
RecommendedListingMarkersView readMarkers(Long userId, String guestSessionId, Long diagnosisId) {
  Diagnosis d = diagnosisRepository.findById(diagnosisId).orElseThrow(DiagnosisNotFoundException::new);
  DiagnosisAccessGuard.requireNotDiscarded(d);
  DiagnosisAccessGuard.requireCompleted(d);   // ← 소유권보다 먼저. read()에는 넣지 않는다
  DiagnosisAccessGuard.requireOwner(d, userId, guestSessionId);
  return listingRecommendationService.recommendMarkersByCriteria(
      criteriaMapper.toCriteria(d, IGNORED_PAGE, IGNORED_SIZE, null));
}
```
더미 값은 `0` 대신 **명명 상수**로(생성자가 size를 검증하지 않아 0도 통과한다). `readMarkers`는 package-private 유지.

**C9.** `DiagnosisFlowService.getRecommendationMarkers(Long, String, Long)`
**C10.** `DiagnosisV2Controller`에 `@GetMapping("/{diagnosisId}/recommendations/map")`.
`principal.userId()`를 직접 역참조하지 않는다(게스트는 주체가 null). 「세 엔드포인트」 javadoc은 2-2와 **한 번에** 「네」로.

### 2-5. 조회 3종 읽기 전용 재구현 (**신규 절**)

> 여기가 "구현은 v2에 맞게 신설"의 본체다. **응답 JSON과 RestDocs identifier는 1바이트도 바꾸지 않는다.**

**`DiagnosisQueryService.java` (신규)** — 조회 3종의 새 소유자.
의존은 **`DiagnosisRepository` 하나**(협력자 7→1: 조회 3종은 `questionCatalog`·`userAccountService`·
`suggestionMessages`·`answerApplier`·`questionTranslator`·`recommendationReader`를 한 줄도 안 쓴다 — 실측).
이관: `HISTORY_SORT_KEYS(:48)` · `getHistory(:98-107)` · `getLatest(:110-118)` · `getDetail(:126-134)` ·
`toResponse(:193-206)` · `toLatestResponse(:208-221)` · `conditionsList(:223-225)`.
시그니처의 primitive `long userId`는 **유지**(회원 전용 시맨틱을 타입으로 적어 둔 것).
`getDetail` 순서는 `findById` → **404 상태 게이트** → **403 소유권**.
```java
// requireCompleted 선행으로 정상 경로 도달 불가 — 손상 문서 500 방지용
monthlyRentMin == null ? 0 : monthlyRentMin
```
> `toResponse`의 이 삼항(`:201-202`)을 **지우지 말 것** — `DiagnosisResponse:27-28`이 primitive `int`라
> 손상 문서에서 언박싱 NPE(500)가 난다. 이름 근거: `chat/application/ChatReportEvidenceQueryService` 선례.

**`DiagnosisAccessGuard.java` (신규, package-private)** — static 3개:
`requireNotDiscarded`(404) · `requireCompleted`(404) · `requireOwner(Diagnosis, Long, String)`(`isOwnedBy` 위임 후 403).
javadoc에 불변식 2개를 못박는다: ① **소유권 wrapper를 새로 만들지 않는다**(#181 — `Reader:82-83` 경고 이관)
② **상태 게이트(404)를 소유권(403)보다 먼저 호출한다.**
`requireCompleted`가 `requireNotDiscarded`의 상위집합이지만 **둘 다 둔다** — v2-3 추천(`read`)의 공개 계약을
이번에 바꾸지 않기 위해서다(A4와 같은 판단).

**`DiagnosisPageRequests.java` (신규, package-private)** — `MAX_PAGE_SIZE=100` 상수화 +
`validatePage` · `validateSort(String, Set<String>)` · `isAscending` · `pageInfo`.
**메시지 키(`validation.min`/`range`/`sortKey`/`sortDirection`)와 인자 순서를 그대로 옮긴다** —
`errors[]` 페이로드가 바뀌면 `diagnosis-history-invalid-input` 스니펫이 깨진다.
**common 승격은 하지 않는다**(§7 후속).

**`DiagnosisRecommendationReader`** — 동작 변화 0. private `requireNotDiscarded`·`requireOwner`·`validatePage`·`validateSort`
본문을 신설 헬퍼 호출로 교체. `SORT_KEYS(:33)`는 이 클래스에 남긴다(추천 정렬 허용키는 추천의 도메인 지식).
**`read()`에 `requireCompleted`를 넣지 않는다**(A4).

**`DiagnosisController`** — 필드 `:41`을 `DiagnosisQueryService`로 교체. 3개 메서드 시그니처·`@RequestParam` 기본값 무변경.
**`principal.userId()` 직접 역참조(`:69`·`:75`·`:81`)는 그대로 둔다** — `AuthPrincipals:11`이 「회원 전용 경로는 이 헬퍼가
필요 없다」고 명시하며, `userIdOrNull`로 미리 바꾸면 다음 사람에게 「permitAll을 깔아도 안전하다」는 잘못된 신호를 준다.
클래스 javadoc `:31-32`의 stateful 문단을 「v2 흐름이 확정해 `diagnoses`에 저장한 진단을 읽는 조회 전용」으로.

**`DiagnosisResponse`·`LatestDiagnosisResponse`** — 존치, 필드·순서·타입 무변경.
**`LatestDiagnosisResponse`에 `@JsonInclude`를 붙이지 않는다** — `completed=false`에서 키는 남고 값만 null인 성질이 곧
계약이고 `DiagnosisDocsFields:612-621` javadoc과 `DiagnosisDocsTest:392-412`의 `isEmpty()` 단정 11개가 그 위에 서 있다.
삭제 후 `DiagnosisResponse.status`는 **`DiagnosisStatus`를 JSON으로 내보내는 유일한 표면**이 된다.

### 2-4. 마이그레이션 — `DiagnosisV1RetireChangeUnit` (order `0124`)

현재 최대 `0123`(`ListingConsentsDropChangeUnit`) 실측 확인.
1. `diagnosisSuggestions` 컬렉션 drop (`collectionExists` 가드)
2. ~~`diagnoses.userId_submittedAt_idx` drop~~ — **철회**(B8)
3. `diagnosisQuestions.active_step_idx` drop
4. `diagnoses`의 `status:'IN_PROGRESS'` → `DISCARDED` 이행 (문서 삭제 아님 — migration-policy §8)
5. `@RollbackExecution` no-op
6. **【신규·필수】** `updateMany({conditions:"NO_ARC"}, {$pull:{conditions:"NO_ARC"}})` — 사실 ⑤. `$pull`이라 멱등.
   **`arcStatus` 스칼라는 건드리지 않는다**(`ArcStatus.NO_ARC`는 살아 있는 정상 값)

> 인덱스 drop을 뺐으므로 「초기화기 삭제와 같은 커밋」 제약은 무효. 다만 **③과 초기화기 `:36-40` 제거는 여전히 같은 커밋**이어야 한다
> (Mongock이 `InitializingBean`이라 같은 기동에서 `ApplicationRunner`보다 먼저 끝난다).
> **심층 방어**: ⑥이 실행됐다는 보장을 코드가 하지 못하므로 `DiagnosisDocument.conditions`를 `Set<String>`으로 낮추고
> 어댑터에서 파싱 실패 값을 걸러 낼지 판단한다(listing이 `ListingV1Controller:28`에서 같은 문제를 그렇게 처리한다).

---

## Phase 3 — 테스트

### 3-1. v1 테스트 정리

- **`DiagnosisDocsTest.java` — 통째 삭제 취소, 절개(13/38 존치).**
  존치 성공 4개: `diagnosis-history(:351)` · `diagnosis-latest(:378)` · `diagnosis-latest-not-completed(:407)` · `diagnosis-detail(:424)`.
  존치 에러 9개: `:670`·`:679`·`:688`·`:698`·`:707`·`:718`·`:729`·`:740`·`:751`.
  **identifier 문자열은 한 글자도 바꾸지 않는다**(operationId = 그 공통 접두사).
  **【블록 절개 불가 — 실측】** `@Test`가 `:184`·`:519` **둘뿐**이고 존치 스니펫이 삭제 블록에서 태어난 변수를 쓴다
  (`diagnosisId(:336)`는 삭제되는 `diagnosis-submit(:324)` 응답에서, `nonStudyToken(:251)`은 삭제되는 step3 블록,
  `ownedId(:528)`는 `createCompletedDiagnosis(:828-843)`). → **두 메서드를 새로 쓴다.**
  픽스처를 v2 흐름으로 교체하되 **세 가지를 반드시 함께**:
  (a) `DiagnosisV2DocsTest`의 `createCompletedDiagnosis`(start + next×6) 이식
  (b) **`seedQuestions` 8건 전체 이식** — 현재 DocsTest는 region·university·district **3건뿐**이라 purpose 답 직후
      `DiagnosisFlowService:272-273`의 `IllegalStateException` → 500. `regionRetry`도 반드시 포함
  (c) **`setUp`에 1-arg 스텁 신규 추가** — 현재 `:178-181`은 2-arg→1-arg 브리지일 뿐이고 1-arg 스텁은
      삭제 대상 블록(`:433`·`:491`)에만 있다. 지역 게이트 `DiagnosisFlowService:283`이 1-arg를 부르므로
      미스텁이면 `null` → `.content()`에서 **NPE 500**(regionRetry로 새는 게 아니다)
  존치 헬퍼: `perform(:845)`·`performWithPathParams(:863)`·`FORGED_TOKEN(:140-149)`·`seedQuestions`·`questionMongoRepository`.
  `diagnosis-latest-not-completed`의 픽스처 전제를 **「확정 진단 0건인 사용자(DISCARDED 1건 보유)」**로 바꾼다
  (v1 쓰기 삭제 후 `diagnoses`에 `IN_PROGRESS`를 만들 경로가 0이 된다).
  삭제 픽스처: `saveAnswer(:817-826)` · `seedSuggestion(:919-935)` · `suggestionMongoRepository(:156`·`:173`·`:175)` ·
  고아 static import **24개**(존재하지 않는 심볼 static import는 spotless가 아니라 **javac 에러**).
  클래스 javadoc(`:111-127`) 전면 재작성 — 이제 「v1 문서 테스트」가 아니라 **「진단 조회 3종 문서 테스트」**다
- **`DiagnosisV2DocsTest.v1DiagnosesStayMemberOnly`(`:815-831`) — 삭제 취소.** 전제가 뒤집혔다.
  `GET /api/v1/diagnoses(:817-820)`·`GET /{diagnosisId}(:827-830)` 두 단정 **존치**, POST 블록(`:822-825`)만 제거.
  컨트롤러가 실제로 등록돼 있으므로 v1에 permitAll이 조용히 추가되면 `principal.userId()` NPE(500)로 401 단정이 깨진다 —
  **진짜 회귀 가드가 된다.** `:812-813`의 「v1 7개」→「v1 조회 3종」. **`:94`·`:109`의 `{@link DiagnosisDocsTest}`는 그대로 둔다**(파일이 살아남는다)
- `DiagnosisMongoIntegrationTest.java` — **Phase 0-3 + 3-7 이관 완료 후** 삭제
- `DiagnosisFlowStepTest`: `v1StepNumbersArePinned(:15-27)`·`stepRoundTrips(:29-39)`를 `ofStep` 없이 `step()`만으로 개작(**삭제 금지**)
- **`http/diagnosis.http` — 파일 삭제 취소.** `#0`·`#1`(`:25-36`) 유지, `#2~#14`(`:38-128`)·`#18`(`:143-146`) 삭제,
  `#15`(이력 `:132`)·`#16`(최근 `:136`)·`#17`(상세 `:140`) 존치. 시드를 v2 `/start`→`/next`×6으로 교체하고
  `#17`의 변수를 6번째 `/next`(COMPLETED 응답)에 이름을 붙여(`### v2-completed`) 갈아 끼운다.
  **`diagnosis-v2.http`로 이전은 기각** — 마커 A-9 번호와 충돌하고 「v1 조회는 v1 파일에」 위치 규약이 깨진다

> **`DiagnosisV2DocsTest:317-336`의 값 단정 블록과 `SAMPLE_VIEW(:1091-1105)`는 절대 손대지 말 것** — 2-1 이관의 전치를 잡는 유일한 가드다.

### 3-2. `DiagnosisDocsFields` 절개 — 멤버 이름 단위

**보존으로 되돌린 것(원안의 삭제 목록 절반)**: `DIAGNOSIS_CONDITION_CODES(:103)` · `HISTORY_SORT_VALUES(:113)` ·
`DETAIL_*(:196-219)` + `detailResponseFields(:222)` + `diagnosisIdPathParameters(:233)` · `diagnosisSummaryFields(:249)` ·
`historyQueryParameters(:340)` · `HISTORY_*(:543-568)` + `historyResponseFields(:570)` · `LATEST_*(:587-610)` + `latestResponseFields(:623)`

**실제 삭제로 남는 것**: `ANSWER_FIELD_CODES(:76)` · `SUGGESTION_ACTION_CODES(:127)` · `SUGGESTION_REASON_CODES(:131)` ·
`SUBMIT_*(:149-180)`+`submitResponseFields(:183)` · `QUESTION_*(:379-423)`+`questionResponseFields(:425)`+`stepPathParameters(:453)` ·
`ANSWER_*(:466-505)`+`answerRequestFields(:511)`+`answerSavedResponseFields(:532)` · `RECOMMENDATIONS_*(:662-691)`+`recommendationResponseFields(:697)`

**계약 정정 2건 (둘 다 도달 불가 값을 광고 중)**
1. `:226` `enumField("data.status", DiagnosisStatus.class)`와 `:576` `enumField("data.content[].status", …)`를
   `codeField(path, List.of("COMPLETED"), …)`로 좁힌다. **두 곳 다 고쳐야 한다**(경로가 달라 병합되지 않으므로 한쪽만 고치면 갈린다)
2. `:103` `DIAGNOSIS_CONDITION_CODES = withNoArc()` → `SELECTABLE_CONDITION_CODES(:91, 8개)`.
   설명 문장 `:264-266`·`:644-646`의 「NO_ARC를 더한다」 절도 제거.
   **`withNoArc()(:139)`와 `LISTING_CONDITION_CODES(:110)`는 건드리지 않는다** — 저쪽 `NO_ARC`는 listing `ConditionTag`의 실제 상수다

**문구 수정 3곳**: `:112`(`DiagnosisService.HISTORY_SORT_KEYS` → `DiagnosisQueryService`) · `:1015` ·
**`:1043`**(경로 문자열이 없어 `grep api/v1/diagnoses`를 **통과하지만** 운영 Swagger의 `resultCode` 스키마 description으로 나간다).
클래스 javadoc `:33-44` 재작성 — **「공유 오퍼레이션」 프레이밍 폐기**(삭제 후 v1·v2가 함께 캡처하는 오퍼레이션이 0개가 된다).
배너 `:146` 삭제, `:193`은 「v1 오퍼레이션」으로.
**절개는 반드시 멤버 이름 단위** — `QUESTION_TABLE(:352)`·`SEED_NOTE(:366)`·`LANGUAGE_NOTE(:371)`가 v1 배너(`:376`) 바로 위인데
`V2_START`/`V2_NEXT_DESCRIPTION`이 문자열 결합으로 쓴다.

### 3-3. 마커 API RestDocs

새 상수: `V2_RECOMMENDATION_MAP_SUMMARY`/`_DESCRIPTION`/`_401`/`_403`/`_404`. **`_400` 배열은 만들지 않는다.**
`SUMMARY`는 **40자 이내**(예: `"v2 진단 추천 전체 마커"`). v2-3 상수 재사용 금지(`page`/`sort`/`title`이 없다).
`total` 설명은 진단 문맥으로 새로(`ListingDocsFields:1002`의 「지도 영역과 필터」는 여기선 거짓).
```java
public static List<FieldDescriptor> v2RecommendationMapFields() {
  return List.of(
      field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
      field("data.markers", JsonFieldType.ARRAY, "추천 조건에 맞는 매물의 지도 마커. 0건이면 빈 배열"),
      optField("data.markers[].listingId", JsonFieldType.STRING, "마커가 가리키는 매물 식별자"),
      optField("data.markers[].lat", JsonFieldType.NUMBER, "마커 위도(WGS84)"),
      optField("data.markers[].lng", JsonFieldType.NUMBER, "마커 경도(WGS84)"),
      field("data.total", JsonFieldType.NUMBER, "…"),
      errorNull());
}
```
- **`ListingDocsFields.mapResponseFields()(:996-1004)`를 복사하지 말 것**(원소가 비-optional)
- **0건 전용 헬퍼를 만들지 말 것** — 같은 `(path, 200)`에 스키마가 둘 생겨 dedup·last-wins로 하나가 조용히 증발한다.
  **테스트는 green이고 `verifyOpenApiSpec`도 스키마는 안 본다**

identifier(공통 접두사 = operationId):
```
diagnosis-v2-recommendations-map      ← 성공(회원). 이 이름이 곧 operationId
diagnosis-v2-recommendations-map-empty / -guest / -forbidden / -not-found / -token-expired
```
URL은 반드시 urlTemplate. 성공·0건·게스트 3건 모두 `pathParameters(...)`, 게스트만 `requestHeaders(guestSessionHeader())`.
에러 3건은 `performWithPathParams(...)`. **모든 스니펫에 `.tag(ApiDocsTags.DIAGNOSIS)`**.
**신규 포트 스텁을 `DiagnosisV2DocsTest(setUp :165-169)`와 `DiagnosisFlowServiceIntegrationTest(:101-105)` 두 곳에 추가**
(원안이 지목한 `DiagnosisMongoIntegrationTest`는 `@Import`에 `DiagnosisFlowService`가 없어 **마커 경로에 도달 자체가 불가능**하고 삭제된다).
`guestFlowWithoutToken(:672~)` 본문에 마커 호출 한 단계를 끼워 넣고 javadoc `:667`을 「네 엔드포인트」로.

### 3-4. `ListingMongoIntegrationTest` — 마커 저장소 가드

**`@MockitoBean`을 쓰는 문서 테스트로는 클램프 우회를 증명할 수 없다. 여기가 유일한 가드다.**
```java
@Test void recommendForMap은_findPage의_100_클램프를_받지_않는다() {
  seedPublishedListings(120);                     // 반드시 100 초과
  var r = listingRepository.recommendForMap(condition, 500);
  assertThat(r.listings()).hasSize(120);          // 100이면 클램프에 걸린 것
}
@Test void recommendForMap은_상한_초과시_빈배열이_아니라_상한까지_채운다() {
  seedPublishedListings(12);
  var r = listingRepository.recommendForMap(condition, 5);
  assertThat(r.listings()).hasSize(5);            // searchForMap 조기 반환 복사 회귀 가드
  assertThat(r.total()).isEqualTo(12);
}
@Test void recommendForMap은_좌표없는_매물을_제외한다() { /* A5 */ }
```
좌표 축 스왑 가드(`lat != lng` 단정으로는 못 잡는다): `lat`는 `33.0~39.0`, `lng`는 `124.0~132.0`.
그리고 **같은 조건으로 `recommendByCriteria`와 `recommendMarkersByCriteria`가 같은 매물 집합을 내는지** 단정 —
"기존과 동일한 조건"의 직접 검증이다.

### 3-5. `DiagnosisFlowServiceIntegrationTest` — 마커 `IN_PROGRESS` 404 (A4)

`@Import`에 `DiagnosisFlowService`·`DiagnosisRecommendationReader`가 있는 유일한 자리.
공개 API로 그 상태를 만들 수 없으므로 `diagnosisRepository.save(Diagnosis.startInProgress(userId))`로 직접 심는다
(`startInProgress`는 `DiagnosisFlowSession:54`가 계속 쓰므로 살아남는다).

### 3-7. `DiagnosisQueryServiceIntegrationTest` (**신규**)

`@DataMongoTest` + Testcontainers. `@Import`는 `{DiagnosisQueryService, DiagnosisRepositoryImpl, SequenceGenerator}` 셋뿐
— **`@MockitoBean`이 0개**(협력자가 저장소 하나라서). 패키지는 반드시 `com.kohere.diagnosis.infrastructure`
(`DiagnosisMongoRepository:10`이 package-private).

① 이력 정렬 asc/desc ② `IN_PROGRESS`·`DISCARDED`가 이력에서 제외 ③ **게스트 진단이 이력·최근에 안 잡힌다**(B2 가드)
④ `latestWhenNone` → `completed=false` + 요약 10필드 null ⑤ 상세 `DISCARDED` → 404
⑥ **상세 `IN_PROGRESS` → 404**(B4 가드) ⑦ 상세 타인 회원 → 403 ⑧ **상세 게스트 진단을 회원 토큰으로 → 403**
(`DETAIL_DESCRIPTION:205`가 문서로만 주장하던 것) ⑨ page/size/sort 위반 → `InvalidInputException` ⑩ `PageInfo` 메타
⑪ **【필수】** `mongoTemplate`로 `{conditions:["PRIVATE_BATH","NO_ARC"], status:"COMPLETED"}` **원시 문서**를 심고
이력·상세가 200인지 단정 — **도메인 빌더로 심으면 enum이라 컴파일이 막혀 이 케이스를 절대 재현할 수 없다**(B9)

### 3-8. 추가 권고 (`DiagnosisV2DocsTest`, non-document)

- `ROLE_ONBOARDING` 토큰으로 `GET /api/v1/diagnoses`가 2xx인지 — `error-response-guide:112`가 명문화한 계약인데
  이를 지키는 코드도 테스트도 **현재 0건**이다
- 삭제 4경로의 실제 status 고정: 인증 토큰으로 `POST /api/v1/diagnoses` → **405**, `GET /api/v1/diagnoses/answers` → **400**,
  `GET /questions/1` → **404**

### 3-6. `http/diagnosis-v2.http`
`:11` 주석·`C-5` 블록(`:317-321`) 삭제. `A-8`(`:198-203`) 뒤에 **`A-9`** 추가(직후가 시나리오 B 배너라 번호 충돌 없음).
```http
### A-9. 추천 매물 전체 지도 마커 — 페이지 없이 마커만. 상한 500 초과 시 잘려서 온다(에러 아님).
#     markers.length < total 이면 상한에 걸린 것이다.
GET {{baseUrl}}/api/v2/diagnoses/{{completed.response.body.$.data.diagnosisId}}/recommendations/map
Authorization: Bearer {{login.response.body.$.data.accessToken}}
```
`C-4`(`:313-316`) 옆에 미확정 진단 → 404 확인 요청 한 줄.

---

## 4. 함정 체크리스트

### 작업 A
| # | 함정 | 회피 |
|---|---|---|
| A-T1 | `size` 검증만 풀면 **100건만 나가는데 테스트가 green** | `findPage`를 안 거치는 저장소 메서드 + 120건 시드 |
| A-T2 | `searchForMap:153-156` 조기 반환 복사 → **200 OK + 빈 지도** | 조기 반환 블록을 넣지 않는다 |
| A-T3 | `IN_PROGRESS` 초안이 게이트를 통과해 **전 매물 스캔** | `requireCompleted`(마커 경로만, 소유권보다 먼저) |
| A-T4 | `GeoPoint(longitude, latitude)` vs Marker `(lat, lng)` — **전부 double이라 전치가 컴파일된다** | `.latitude(), .longitude()` 순서 복사 + 좌표 범위 단정 |
| A-T5 | 프로젝션 + `ListingMongoMapper.toDomain` → **즉시 NPE** | 범위 밖(A11). `.fields()` 호출 자체를 넣지 않는다 |
| A-T6 | diagnosis가 `ListingMapResponse`/`ListingMapSearchResult` import → **ModularityTest 실패** | published view는 `listing.api`에 |
| A-T7 | 0건 스니펫에 비-optional 원소 기술자 → **SnippetException** | 배열은 `field`, 원소는 `optField` |
| A-T8 | 성공/0건이 다른 헬퍼 → **200 스키마 하나가 조용히 증발**(green) | 세 200 스니펫이 같은 헬퍼 하나 |
| A-T9 | 신규 포트 스텁 누락 → NPE 500 → **오퍼레이션이 Swagger에서 누락** | `DiagnosisV2DocsTest`·`DiagnosisFlowServiceIntegrationTest` 두 곳 |
| A-T10 | 스니펫 하나가 태그 누락 → 빌드 실패, **원인 미표시** | 전 스니펫에 `.tag(...)` |
| A-T11 | URL 문자열 이어붙이기 → **유령 경로가 OpenAPI에 등록**(빌드 통과) | urlTemplate + `pathParameters` |
| A-T12 | "400은 발생하지 않는다" 단언 | 「`INVALID_INPUT`은 발생하지 않는다」로 좁혀 쓴다 |

### 작업 B
| # | 함정 | 회피 |
|---|---|---|
| B-T1 | `DiagnosisMongoIntegrationTest`를 먼저 지우면 **v2 공유 컴포넌트 회귀 가드가 0** | Phase 0-3 + 3-7 선행 |
| B-T2 | 02 스펙 §7을 먼저 지우면 **v2 추천 카드 계약이 문서에서 증발**(`:805` 플레이스홀더) | Phase 0-1 선행 |
| B-T3 | `DiagnosisMongoRepository:12` 삭제 시 `DiagnosisFlowServiceIntegrationTest:254` 컴파일 실패 | 그 테스트를 먼저 고친다 |
| B-T4 | 레코드 이관 시 인접 `int` 4개·`double` 2개 **전치가 타입으로 안 잡힘** | 순수 cut/paste. 가드는 `DiagnosisV2DocsTest:317-336`(**삭제 금지**) |
| B-T5 | ~~인덱스 부활~~ | **무효** — 인덱스를 drop하지 않는다(B8) |
| B-T6 | `DiagnosisQuestionIndexInitializer`/`DiagnosisFlowSessionIndexInitializer`를 같이 지우면 **v2 기동 중단** | 절대 건드리지 않는다 |
| B-T7 | `DocsFields`를 「배너부터 아래로」 지우면 `QUESTION_TABLE`·`SEED_NOTE`·`LANGUAGE_NOTE`가 함께 날아가 **V2_START/V2_NEXT description이 깨진다** | 멤버 이름으로 절개 |
| B-T8 | `:1043`은 경로 문자열이 없어 `grep api/v1/diagnoses`를 **통과하는데** 운영 Swagger에 나간다 | 「v1」 문자열로도 grep |
| B-T9 | **`DiagnosisRepositoryImpl`에서는 고아 import가 발동하지 않는다**(`:47-69`가 전부 쓴다 — 원안대로 지우면 컴파일 실패). 진짜 발동처는 `DiagnosisController`(9개)와 `DiagnosisDocsTest`(24개)이며 후자는 **spotless가 아니라 javac 에러**다 | `DocsFields` 멤버 삭제 · `DocsTest` 스니펫 삭제 · suggestion 파일 삭제를 **한 커밋**에 |
| B-T10 | dangling `{@link}` — **doclint 게이트가 0건** | 리뷰 체크리스트 |
| B-T11 | `components.schemas` 이름 오염 — **이력 400 스니펫이 살아남아 공용 400 스키마(`api-v1-diagnoses-1676351150`, 9곳 `$ref`)의 이름 출처가 유지될 여지가 생긴다. 다만 보장은 아니다** — 이름은 참조 경로가 아니라 **스니펫 파일 순회 순서**에서 온다(반례: 401 공용 스키마가 `api-v1-chat-rooms-…`). 403/404 공유 스키마는 출처 경로가 삭제되므로 **어차피 개명된다** | 키 diff는 **여전히 필수** |
| B-T12 | v1 부분 존치를 이유로 v2 identifier 개명 | B7 — ADR-0017 `:42`에 문장으로 못박는다 |
| B-T13 | **stale build 산출물** — 트리에 v1 스니펫 61개와 v1 참조 34곳이 살아 있다 | 검증 첫 줄이 `clean` |
| B-T14 | **삭제 후 404가 아니다.** `@GetMapping("/{diagnosisId}")`가 남아 형제 경로가 매칭된다 — 인증 기준 **POST 2개는 405**, `GET /answers`·임의 문자열은 **400 MALFORMED_REQUEST**, `/questions/{step}`·`/{id}/recommendations`만 **404**. 익명은 전부 401 | 프런트 공지를 **경로별 표**로 |
| B-T15 | **`DocsTest` 통째 삭제 시 살아 있는 API 3개가 Swagger에서 증발하는데 빌드 green** — `verifyOpenApiSpec`은 존재하는 오퍼레이션만 검사하고 「있어야 할 것이 없다」를 안 본다. asciidoc `index.adoc`도 없어 다른 게이트 0건 | 3-1 절개 + §6의 존치 확인 grep |
| B-T16 | `DocsTest`는 **`@Test` 2개짜리 순차 체인**이라 블록 절개 불가(존치 스니펫이 삭제 블록의 변수를 쓴다) | 두 메서드를 새로 쓴다 |
| B-T17 | 문항 카탈로그 **3건**으로 v2 흐름을 돌리면 두 번째 `/next`에서 `IllegalStateException` → 500 | `seedQuestions` 8건 전체 이식 |
| B-T18 | `setUp`의 listing 스텁은 2-arg 브리지뿐 — 미스텁이면 **지역 게이트에서 NPE 500**(regionRetry가 아니다) | 1-arg 스텁 신규 추가 |
| B-T19 | **legacy `conditions:["NO_ARC"]` 문서가 조회 3종을 500으로 죽인다** | ChangeUnit ⑥ `$pull` + 어댑터 심층 방어 + **원시 문서 시드 회귀 테스트**(3-7 ⑪) |
| B-T20 | **인덱스 존치를 지키는 테스트가 0건**(`@Profile("!test")`) — 원안대로 drop해도 build가 green | 완료 판정에 dev `getIndexes()` **수동 확인** |

---

## 5. 커밋 분할

| # | 타입 | 내용 | 단독 green |
|---|---|---|---|
| 1 | `test(diagnosis)` | Phase 0-3 — 공유 컴포넌트 커버리지 이관 + `findFirstByUserIdAndStatus` 의존 제거 | ✅ 필수 |
| 2 | `docs(diagnosis)` | Phase 0-1·0-2 — 계약·AC 이관. **삭제 0건** | ✅ |
| 3 | `refactor(diagnosis)` | 2-1 — 중첩 record 3개 이관 후 `RecommendationResponse` 삭제. **스니펫 diff 0바이트** | ✅ 필수 |
| 4 | `refactor(diagnosis)` | **2-5 — 조회 3종을 읽기 전용 서비스로 분리(동작 변경 없음).** 신설 3파일 + Reader 위임 교체 + 컨트롤러 필드 교체. **스니펫 diff 0바이트** | ✅ 필수 |
| 5 | `feat(diagnosis)!` | 2-2 — v1 쓰기·추천 4경로 제거 + **상세 게이트 `COMPLETED` 전용(200→404)**. 유일한 동작 변경을 커밋 메시지에 명시 | ✅ |
| 6 | `chore(migration)` | 2-4 — ChangeUnit `0124`(③과 초기화기 `:36-40` 제거는 같은 커밋) | ✅ |
| 7 | `docs` | Phase 1-1·1-2 — v1 문서 정리 + ADR 배너 + 규약 명문화 | ✅ |
| 8 | `test(diagnosis)` | 3-1·3-2·3-7·3-8 — DocsTest 절개 · DocsFields 절개 · suggestion 파일 삭제 · QueryService 통합 테스트. **static import 컴파일 의존 때문에 반드시 한 커밋** | ✅ |
| 9 | `docs(diagnosis)` | Phase 1-3 — 마커 v2-4 신설 + 규약 예외 | ✅ |
| 10 | `feat(diagnosis)` | 2-3 — 마커 API 코드 | ✅ |
| 11 | `test(diagnosis)` | 3-3~3-6 — 마커 RestDocs + 저장소 통합 + `.http` | ✅ |

`Refs: #<이슈번호>` 푸터 · PR 본문 `Closes #N`.
**PR 본문 필수 기록**: ① 인덱스 부재가 마커의 진짜 병목(§7) ② 순차 id + `permitAll` 존재 오라클은 기존 성질이며 넓히지 않음
③ `components.schemas` 키 diff ④ 삭제 전 `db.diagnoses.countDocuments({status:'IN_PROGRESS'})`
⑤ 삭제 전 CloudWatch Logs Insights `pathPattern like /api/v1/diagnoses` 집계 —
**베이스라인은 삭제 전에만 얻을 수 있다**(`AccessLogInterceptor:51-52`가 `BEST_MATCHING_PATTERN`을 남겨 매핑이 사라지면
오타·스캐너 404와 구분되지 않는다) ⑥ **`AnswerSavedResponse`가 boolean 하나뿐이라 `IN_PROGRESS` id를 발급한 표면이 0이었다**(B4 안전성 근거)
⑦ 삭제 전 `db.diagnoses.countDocuments({conditions:"NO_ARC"})` ⑧ 배포 후 `db.diagnoses.getIndexes()`에 `userId_submittedAt_idx` 존재 캡처

---

## 6. 완료 판정

```bash
./gradlew clean build          # ← clean 필수 (B-T13)

# 삭제 4경로만 겨냥 (전체 grep은 무효 — 3경로가 의도적으로 남는다)
grep -rn "diagnoses/answers\|diagnoses/questions" src/ docs/ http/
grep -rn "api/v1/diagnoses/{[^}]*}/recommendations" src/ docs/ http/
grep -rn "POST /api/v1/diagnoses\b" docs/ src/

# 존치 확인
grep -c "^  /api/v1/diagnoses" build/api-spec/openapi3.yaml     # == 3

# operationId 기대값 7종 (이름 그대로)
grep -n "operationId: diagnosis" build/api-spec/openapi3.yaml
#   diagnosis-history · diagnosis-latest · diagnosis-detail
#   diagnosis-v2-start · diagnosis-v2-next- · diagnosis-v2-recommendations · diagnosis-v2-recommendations-map
#   ※ diagnosis-v2-next- 의 후행 하이픈은 정상값이다(identifier가 여러 갈래라 공통 접두사가 그렇게 된다).
#     후행 하이픈 금지는 신규 마커 오퍼레이션에만 적용된다.

grep -rn "v1" src/test/java/com/kohere/docs/DiagnosisDocsFields.java   # 전수 분류 (:1043은 경로 grep을 통과)
# components.schemas 키 diff — 삭제 전/후 비교해 PR 본문에 첨부
```
**수동 확인**
- dev에서 `db.diagnoses.getIndexes()`에 `userId_submittedAt_idx` **존재**(B-T20 — 이를 지키는 테스트가 0건이다)
- `db.diagnoses.countDocuments({conditions:"NO_ARC"})` **== 0**
- Swagger UI에서 마커 오퍼레이션의 `X-Guest-Session-Id`·`diagnosisId` 설명, `data.markers[]`가 `oneOf`로 붕괴하지 않았는지
- `http/diagnosis.http` `#15~#17`과 `diagnosis-v2.http` `A-9`를 dev에 실행

> **권고**: `verifyOpenApiSpec`에 `REQUIRED_OPERATION_IDS` 집합 검사 3줄을 추가한다 — `seenOperationIds`가 이미 있으므로
> `doLast` 말미에서 차집합을 던지면 **「사라진 오퍼레이션」을 처음으로 잡는 게이트**가 생긴다(B-T15).

---

## 7. 범위 밖 / 후속

- **인덱스 부재가 마커 API의 진짜 병목** — `ListingMongoIndexInitializer:42-72`에 `address.city`·`address.district`·
  `nearbyUniversityCodes`가 없고 `(favoriteCount, updatedAt)` 커버 인덱스도 없다. 상한은 fetch만 막고 **count와 sort는 전량**이다
- **Mongo 프로젝션(`_id`+`location`)** (A11)
- **게스트 레이트리밋 부재** — `/api/v2/diagnoses/**`는 `permitAll`이고 진단 세션에 TTL이 없다
- **진단 데이터 보존·파기 방침** — 조회 3종이 남아 「읽을 수단이 있다」는 성립하지만, 탈퇴가 `diagnoses`를 지우지 않고
  (`UserWithdrawnEventListener:38-46`) TTL도 없어 **지울 수단이 없다.** `database-design.md` §11 미확정 항목으로 승격
- **`validatePage`/`validateSort`를 `common`으로 승격** — `BookingService:159`·`ChatRoomListService:115`·
  `ListingService:382`·`UserBlockServiceImpl:84`까지 6곳 별건(이번엔 diagnosis 내부 2곳만 통합)
- **이력 정렬 tie-break + `{userId:1, submittedAt:-1, _id:1}` 인덱스 확장** — 반드시 한 쌍으로
- **403/404 존재 오라클** — 소유권 403 / 미존재 404 + 전역 순차 채번이 「확정 진단의 존재와 총량」을 회원에게 열거 가능하게 한다.
  **이번엔 넓히지 않는다**(같은 분기를 그대로 쓰고 새 구분 신호를 만들지 않는다). 접으려면 spec 소유권 표 3행 · `DETAIL_403/404` ·
  `diagnosis-detail-forbidden` 스니펫 · US-2-3 AC · `Reader`까지 함께 바꾸는 계약 변경이다 —
  **가드 클래스 javadoc과 PR 본문에 「수용한다」를 근거와 함께 기록**해 무결정 통과를 막는다
- `RecommendationCriteria` 필터 분해 (A12)
