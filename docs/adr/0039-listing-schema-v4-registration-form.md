# ADR-0039. 매물 스키마를 등록 폼 기준 v4로 재정의하고 마이그레이션 체인을 baseline으로 리셋한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0039 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-12 |
| 기준 코드 | `feature/220-listing-registration-api` @ `04913ea`. 본 ADR의 수치·파일 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0001](./0001-bounded-context-module-decomposition.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0008](./0008-mysql-migration-flyway.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0015](./0015-sensitive-column-encryption.md), [ADR-0028](./0028-diagnosis-questions-catalog-store.md), [ADR-0032](./0032-mongodb-migration-runner.md), [ADR-0033](./0033-business-registry-verification.md), [ADR-0034](./0034-landlord-phone-sms-verification.md), [ADR-0036](./0036-diagnosis-v2-server-driven-flow.md), [ADR-0037](./0037-listing-localization-and-code-catalog.md), [migration-policy](../database/migration-policy.md), [database-design](../database/database-design.md) |

## Status

Proposed

## Context

`listings` v3 스키마는 외부 수급 데이터를 전제로 만들어졌다. 임대인이 직접 매물을 등록하는 폼이 확정되면서, 저장 스키마와 실제로 수집하는 정보 사이에 세 종류의 어긋남이 드러났다.

| 어긋남 | 실태 |
|---|---|
| **채울 수 없는 필드** | `roomOffers[].inventory`(재고 3필드)·`propertyPolicies` 5필드·`facilities.commonSpaces[].count` 등은 등록 폼이 수집하지 않고 채울 경로도 없다 |
| **저장할 곳이 없는 입력** | 담당자 연락처·사업자등록번호·블로그 URL·이용 연령대·지원 언어·주변 시설·설문 3종은 폼이 받는데 스키마에 자리가 없다 |
| **값 목록 불일치** | `ListingType.OTHER`·`RentalType.JEONSE`·`BuildingType.GOSHIWON`·`HeatingSystem.DISTRICT`·`TransitType.BUS`는 폼 선택지에 없고, 반대로 건물 형태 4종은 폼에만 있다 |

세부 사항이 얽혀 있어 개별로 판단하기 어려웠던 지점이 넷 더 있었다.

1. **`ConditionTag.NO_ARC`가 필드가 아니라 태그로 표현된다.** 저장은 `propertyPolicies.arcRequired`(boolean)인데 응답에서는 `NO_ARC` 태그로 파생하고, 진단은 `arcStatus`에서 동명의 `DiagnosisCondition.NO_ARC`를 파생해 `conditions`에 주입한다. 한 사실이 세 모듈에서 세 가지 모양으로 존재한다.
2. **`refundPolicy`가 `{code, description{ko,en}}`인데 `code`가 쓰이지 않는다.** 등록 폼은 문장 하나를 받는다.
3. **`descriptions` VO가 번역 대상(`ko`/`en`)과 비번역 대상(`extraNotes`)을 한 객체에 담고 있다.**
4. **`address.district`가 자유 텍스트다.** 진단 `District` enum은 5구 + `ETC` 6종인데, 실제 매물 57건의 `address.district`는 9종이다. 추천 질의가 `Criteria.where("address.district").is(district)`로 **문자열 등가 비교**를 하므로 `ETC`를 고르면 매칭되는 문서가 없어 **항상 0건**이다 — 정작 "그 외"에 해당하는 매물이 28건(49%) 있는데도 그렇다.

이행 조건도 제약이다. 앱은 이미 출시됐고, v3 문서를 v4로 옮기는 필드 대응이 존재하지 않는 신규 필드(담당자 연락처·설문 3종 등)를 포함해 **점진 이행으로는 채울 수 없다**. 한편 [migration-policy §8](../database/migration-policy.md)과 [ADR-0032](./0032-mongodb-migration-runner.md)는 컬렉션 통째 drop을 금지한다.

## Decision

**매물 스키마를 등록 폼 기준 v4로 재정의하고, listing 마이그레이션 체인을 v4 baseline으로 리셋한다. 시드 데이터는 수동으로 주입한다.**

### 1. 스키마 v4 (루트 34필드)

- **삭제** — `propertyPolicies`(VO 전체) · `roomOffers[].inventory` · `descriptions`(VO) · `RefundPolicy`(VO) · `CommonSpace.count` · 매물 공통 `contract`
- **추가** — `contact{managerName, phone, sms}` · `businessRegistrationNumber` · `blogUrl` · `ageMin` · `ageMax` · `rejectionReason` · `languagesSupported` · `nearbyFacilities` · `preferredNationalities` · `contractDifficulties` · `serviceFeedback` · `roomOffers[].contract`
- **이동·변형** — `arcRequired`를 루트로 승격(boolean → `ArcRequirement` enum) · `contract`를 `roomOffers[]` 하위로 · `description`/`extraNotes` 분리 · `refundPolicy`를 `LocalizedText` 문장 하나로 · `facilities.commonSpaces`를 `Set<CommonSpaceType>`으로
- `roomOffers[].pricing.deposit`은 **유지한다.** booking의 `totalAmount = deposit + monthlyRent × 계약 개월수` 계약이 이 값을 전제하므로, 등록 폼에 보증금 입력을 추가하는 쪽으로 맞춘다.
- `imageUrls`·`roomOffers[].roomImageUrls`는 **저장 스키마에는 그대로 남지만 요청 본문에서는 빠졌다.** 등록 요청은 URL 문자열 대신 미리 올려 둔 사진의 저장 키(`imageKeys`·`roomOffers[].roomImageKeys`)를 받고, 서버가 그 사진을 확정 위치로 옮긴 뒤 URL을 채운다 — [ADR-0041](./0041-listing-image-upload-to-s3.md)이 이 부분을 개정한다. 저장 형태·불변식(`roomImageUrls` 최소 2장)은 바뀌지 않는다.
- `roomOffers[].pricing.weeklyRent`는 **받지 않는다.** 등록 폼에 주 단위 가격 칸이 있어 한때 필드로 두었으나, 이 값을 받으면 임대 유형에 주간 계약(`RentalType.WEEKLY_RENT`)이 생기고 월세·주세 중 **하나만** 받는 구조로 가야 한다. 그런데 예산 필터(`minBudget`·`maxBudget`)·정렬(`PRICE_ASC`)·예약 총액이 전부 `monthlyRent` 전제로 짜여 있어, 필드만 받으면 **저장·표시만 되고 아무것도 결정하지 못하는 값**이 된다(주 단위 전용 매물은 `monthlyRent=0`이 되어 예산 필터에 0원 매물로 걸린다). 주 단위 임대는 `RentalType` 확장과 함께 별도로 다룬다.

`ConditionTag.NO_ARC`를 없애고 `arcRequired`(`REQUIRED`/`NOT_REQUIRED`) 필드 하나로 통일한다. 응답 파생·진단 파생 주입을 모두 제거해 `filterTags` 저장값과 응답 태그가 1:1이 된다.

### 2. 진단↔매물은 값 집합이 아니라 **매핑**으로 잇는다

두 모듈은 각자의 입력 어휘를 보유한다([ADR-0001](./0001-bounded-context-module-decomposition.md)). 값 집합을 강제로 일치시키지 않고 매핑 규칙을 정의한다.

| 진단 | 매물 | 매핑 |
|---|---|---|
| `Region`(3종) | `address.city`(`City`, 카탈로그 3종) | 등가 비교 |
| `District`(5구 + `ETC`) | `address.district`(`District`, 카탈로그 9종) | 5구는 등가 비교, **`ETC`는 그 5구의 여집합(`$nin`)** |
| `ArcStatus`(2종) | `arcRequired`(`ArcRequirement` 2종) | `NO_ARC → NOT_REQUIRED`, `ARC_ISSUED → 필터 미적용` |

`District.ETC`는 "선택지에 없는 그 외"라는 뜻이므로 진단 선택지를 매물 9종에 맞춰 늘리지 않는다. 리터럴 비교를 여집합 질의로 고치는 것으로 충분하며, 이 수정으로 지금까지 도달 불가였던 28건이 정상 매칭된다.

`ArcRequirement`에서 `OTHER`를 제거해 `ArcStatus`와 2:2로 맞춘다 — 등록 폼 선택지에 없던 값이다.

### 3. PII를 매물 문서에 저장한다

`businessRegistrationNumber`는 **원문**을, `contact.phone`/`sms`/`managerName`은 평문을 `listings` 문서에 저장한다. [ADR-0033](./0033-business-registry-verification.md)의 "원문 비저장·해시로만 영속"을 **매물 문서 한정으로 개정**한다(온보딩·`users` 테이블에는 여전히 미채택, `auth`의 검증은 무상태 유지).

`contact`는 **세입자 응답에 공개**한다. 매물별 담당 연락처는 임대인 개인 연락처(`users.phone_number`, 마스킹 대상 — [ADR-0034](./0034-landlord-phone-sms-verification.md))와 **별개 값**이므로 마스킹 대상이 아니다. `businessRegistrationNumber`와 설문 3종(`preferredNationalities`·`contractDifficulties`·`serviceFeedback`)은 응답에서 제외한다.

### 4. 이행 — listing 마이그레이션 체인을 v4 baseline으로 리셋한다

**`0099`~`0114` changeUnit을 삭제하고, `0115 listing-v4-baseline` 하나로 갈음한다.** v3 데이터가 폐기되는 이상 그 데이터를 v1→v2→v3로 옮기던 이력은 재현할 대상이 없다. [migration-policy §1](../database/migration-policy.md#1-기본-규칙)이 인정하는 **baseline** 채택(`V1__baseline.sql` 대응)을 MongoDB에 적용한 것이다.

**존치: `0100 SearchPlaceSeedChangeUnit`.** 유일하게 다른 컬렉션(`searchPlaces`)을 다루며 v4와 무관하다. 나머지 15건은 전부 `listings`·`listingCatalog`만 건드리는데, 두 컬렉션은 v4에서 통째로 재정의된다.

기존 환경의 Mongock changelog에는 `0099`~`0114` 항목이 남지만, 대응 클래스가 없으면 실행 대상에서 빠질 뿐이라 무해하다.

**`0115`는 스키마만 다룬다** — v4 validator 적용(컬렉션이 없으면 `createCollection`+validationOptions, 있으면 `collMod`)과 옛 인덱스 2건 삭제. **데이터는 건드리지 않는다.**

**시드는 수동으로 주입한다**(`listings` 2건 · `listingCatalog` 103건). 운영 절차는 [migration-policy §8-1](../database/migration-policy.md#8-1-시드-주입-절차)이 정본이며, 핵심은 **`--drop`을 쓰지 않는 것**이다 — 컬렉션을 지우면 validator가 함께 사라지는데 `0115`는 1회성이라 재기동해도 복구되지 않는다. `deleteMany({})` 후 import한다.

인덱스는 부트스트랩(`ListingMongoIndexInitializer`)이 계속 소유한다. 다만 `listings_status_arc_required`는 키가 바뀌므로 **새 이름으로 만든다** — 같은 이름·다른 키는 멱등 생성으로 갱신되지 않고 `IndexOptionsConflict`가 난다.

v4 `$jsonSchema`는 `0115` 안에 **동결**한다. `ListingMongoIndexInitializer`의 정적 메서드를 호출하지 않는다 — `0105`가 `listingJsonSchema()`를 정적 호출한 탓에 v3에서 메서드를 포크해 `listingV2JsonSchema()`라는 죽은 사본이 생긴 전례가 있다. 체인 삭제로 두 정적 메서드는 사용처가 사라지므로 함께 제거한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. v4 재정의 + baseline 리셋 + 수동 시드** | 스키마가 실제 수집 정보와 일치. 죽은 필드·파생 태그 소멸. 체인이 1건으로 줄어 재생 위험(`0109`의 v4 다운그레이드 등)이 구조적으로 사라진다 | 운영 데이터 폐기. v3 이전 이력을 체인으로 재현 불가 | **채택** |
| B. v3 유지 + 신규 필드만 추가 | 이행 불필요, 정책 위반 없음 | 채울 수 없는 필드 22개가 영구히 남고, 등록 API가 그 값을 날조해야 한다 | 미채택 — 문제의 원인을 그대로 둔다 |
| C. expand-contract 점진 이행 | 무중단, [migration-policy §5](../database/migration-policy.md) 준수 | 신규 필수 필드(담당자 연락처·설문)에 **대응하는 원본 값이 없어** 백필이 불가능하다 | 미채택 — 기술적으로 성립하지 않는다 |
| D. 시드도 changeUnit이 적재 | 신규 환경이 즉시 정상 동작 | 시드가 jar에 고정돼 운영자가 고치려면 재빌드·재배포가 필요하다. `listingCatalog`는 정책상 가능하지만 `listings`는 곧 임대인 데이터가 오갈 컬렉션이라 코드가 소유하면 안 된다 | 미채택 — 시드를 자유롭게 바꿀 수 있어야 한다는 요구와 어긋난다 |
| E. `District`를 매물 9종에 맞춰 진단 확장 | 두 모듈의 값 집합 일치 | 진단 enum·문항 시드·사용자 데이터 3곳을 고쳐야 하고, `ETC`("그 외")의 의미를 잃는다 | 미채택 — 매핑 한 곳으로 풀리는 문제다 |
| F. 기존 체인을 no-op으로 무력화 | 클래스가 남아 이력 추적이 쉽다 | 빈 껍데기 14개가 남아 "왜 비어 있지"를 영구 유발하고, 적용된 마이그레이션의 **동작을 사후 변경**해 6개월 뒤 이력만 보고 재현하려는 사람에게 거짓말을 한다 | 미채택 — 삭제가 더 정직하다 |

## Consequences

- **긍정**: 저장 스키마가 등록 폼과 1:1이 된다. `NO_ARC` 파생이 사라져 한 사실이 한 곳에만 존재한다. `District.ETC` 여집합 매핑으로 도달 불가였던 매물 28건이 추천에 노출된다.
- **부정/트레이드오프**
  - 기존 매물 데이터를 폐기한다. 재시드 전에는 애플리케이션이 정상 동작하지 않으므로 **도메인 변경과 저장 변경이 한 배포에 묶인다.**
  - [migration-policy §8](../database/migration-policy.md)·[ADR-0032](./0032-mongodb-migration-runner.md)·[ADR-0008](./0008-mysql-migration-flyway.md) D3·[ADR-0005](./0005-polyglot-persistence.md) D7의 "파괴적 일괄 변경 금지"에 **1회 예외**를 만든다. 각 문서에 예외 범위를 명시한다.
  - **적용된 changeUnit 15건(`0099`~`0114`, `0100` 제외)을 삭제한다.** 코드에서 사라지므로 v3 이전 환경을 체인으로 재구성할 수 없다 — 되돌리려면 git 이력을 봐야 한다. 기존 환경의 changelog에는 고아 항목이 남는다.
  - 매물 문서에 임대인 PII가 새로 생긴다. [ADR-0015](./0015-sensitive-column-encryption.md)의 민감정보 범위와 at-rest 대상에 MongoDB를 추가한다.
  - 응답 구조가 하위 호환을 깬다. 구버전 앱 대응은 별도 ADR(API 버전 분리)에서 다룬다.
- **후속 작업**
  - 임대인 탈퇴 시 매물 문서 PII 처리 — [ADR-0014](./0014-withdrawal-pii-anonymization.md)의 익명화 대상이 MySQL `users` 컬럼뿐이라 `contact`·사업자등록번호가 남는다. **임대인 탈퇴 기능 구현 시 함께 설계한다.**
  - 관리자 승인(`PENDING → PUBLISHED`/`REJECTED`) API. **승인 조건에 `location` 보유를 넣어** 좌표 없는 매물이 공개되지 않게 한다.
  - 지오코딩으로 `location`·`nearbyUniversityCodes` 채우기.

## Validation

- 기동 시 `0115`가 v4 validator를 적용하고, 옛 인덱스 2건이 사라지며 새 인덱스가 생성된다.
- **신규 환경 시나리오**: 빈 DB 기동 후 `0100`·`0115`만 실행되고, 시드를 `deleteMany({})`+import로 주입한 뒤 validator가 유지된다(`--drop` 금지 확인).
- **기존 환경 시나리오**: changelog에 `0099`~`0114` 고아 항목이 남은 채로 `0115`만 실행되고, 최종 상태가 신규 환경과 같다.
- **매핑 회귀 테스트**: `District.ETC`로 추천을 조회하면 명시 5구를 제외한 매물이 반환된다(현재는 0건).
- `ApplicationModules.verify()` 통과 — `listing`↔`diagnosis`가 여전히 직접 의존하지 않는다.
