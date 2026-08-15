# Migration Policy

> 스키마/데이터 변경을 **버전 관리·재현·검증** 가능하게 하는 운영 규칙의 정본이다. 도구 결정은 **[ADR-0008](../adr/0008-mysql-migration-flyway.md)**(MySQL=Flyway, 폴리글랏 전략), 스키마 정의는 [database-design](./database-design.md), 영속 배치는 [ADR-0005](../adr/0005-polyglot-persistence.md)·[ADR-0006](../adr/0006-refresh-token-store-redis.md).

## 목적

폴리글랏(MySQL·MongoDB·Redis)에서 **누가·언제·어떻게 스키마를 바꿨는지**를 추적·재현하고, **로컬↔클라우드 동일 스키마**와 **무중단 배포**를 보장한다. 변경은 사람이 손으로 DB를 만지는 것이 아니라 **마이그레이션 산출물**로만 한다 — 유일한 예외는 [§8](#8-mongodb-변경-관리)에 범위·근거와 함께 명시한 MongoDB 1회 수동 재시드다.

## 0. 스토어별 개요

| 스토어 | 도구/방식 | 변경 단위 | 비고 |
| --- | --- | --- | --- |
| **MySQL** | **Flyway**(`flyway-core`+`flyway-mysql`) + JPA `ddl-auto=validate` | 버전드 SQL(`V__`) | DDL은 Flyway만, JPA는 검증만(§1~§7) |
| **MongoDB** | 인덱스 부트스트랩 + **Mongock**(`@ChangeUnit`, 모듈별) + 앱 레벨 `schemaVersion` | 인덱스 생성·문서 진화·1회성 이행 | 스키마리스 — DDL 없음(§8) |
| **Redis** | 코드 상수(키스페이스·TTL) | 키 네임스페이스 버전 | 스키마 없음 — 별도 마이그레이션 없음(§9) |

---

## MySQL (Flyway)

### 1. 기본 규칙

- **forward-only**: 롤백 스크립트에 의존하지 않는다. 잘못된 변경은 되돌리는 새 마이그레이션(`V{n+1}`)으로 고친다.
- **불변(immutable)**: 한 번 적용·머지된 마이그레이션 파일은 **수정하지 않는다**(체크섬 깨짐). 변경은 항상 새 버전. 이 조항의 근거는 Flyway의 체크섬 검증이므로 **MySQL/Flyway 전용**이다 — 체크섬이 없는 MongoDB(Mongock)에는 같은 조항이 없다([§8](#8-mongodb-변경-관리)).
- **1 변경 = 1 파일**: 논리적으로 하나인 변경을 한 파일에 모으고, 무관한 변경을 섞지 않는다.
- **DDL은 Flyway만**: 애플리케이션·사람이 직접 `ALTER` 하지 않는다. Hibernate는 `spring.jpa.hibernate.ddl-auto=validate`로 **검증만** 한다.
- **결정성**: 모든 환경(로컬·CI·클라우드)이 같은 마이그레이션 집합으로 동일 스키마를 만든다.
- **baseline**: 도입 시점의 기존 스키마는 `V1__baseline.sql`(또는 Flyway baseline)로 채택한다.

### 2. 파일 네이밍 & 배치

- 위치: `src/main/resources/db/migration`.
- 버전드: `V{버전}__{스네이크_설명}.sql` (예: `V2__add_social_accounts.sql`). 버전은 단조 증가.
- 반복(repeatable): `R__{설명}.sql` (뷰·함수 등 매번 재적용해도 안전한 객체). 버전드 이후 적용.
- 설명은 동사+대상으로 명확히(예: `add_users_terms_version`, `create_idx_posts_board_created`).

### 3. 되돌릴 수 있는 변경 (호환성 분류)

| 분류 | 예 | 배포 안전성 |
| --- | --- | --- |
| **호환(확장)** | 컬럼 추가(nullable/default), 인덱스 추가, 새 테이블 | 구버전 앱과 공존 가능 — 먼저 배포 |
| **비호환(축소/변경)** | 컬럼 제거·rename·타입 변경, `NOT NULL` 전환, 제약 강화 | 구버전 앱을 깨뜨림 — **expand-contract**(§5)로 분리 |

원칙: **한 릴리스에서는 호환 변경만**. 비호환 변경은 §5 절차로 여러 릴리스에 나눈다.

### 4. NOT NULL 컬럼 추가 절차

기존 데이터를 깨지 않도록 **3단계**로 나눈다.

1. **확장**: `nullable`(또는 `DEFAULT`) 컬럼으로 추가(`V_a`). 새 코드가 값을 채우기 시작.
2. **백필**: 기존 행을 채우는 데이터 마이그레이션(`V_b`, 배치 가능). 모든 행이 값 보유.
3. **축소**: `NOT NULL` 제약 부여(`V_c`). (대형 테이블은 §6 주의.)

> 예: `users.terms_version`/`agreed_at`(Consent)·`social_accounts.email`처럼 나중에 추가되는 컬럼은 1→2→3 순으로.

### 5. Expand-Contract 패턴 (무중단)

비호환 변경(컬럼 rename·제거·타입 변경)을 **무중단**으로:

1. **Expand**: 새 컬럼/구조를 호환 추가하고, 앱이 **신·구 양쪽에 쓰기**(dual-write)·신 우선 읽기.
2. **Migrate**: 기존 데이터를 신 구조로 이행(배치).
3. **Contract**: 구 컬럼/구조를 제거(다음 릴리스). 이 단계는 모든 인스턴스가 신 구조만 쓰는 것을 확인한 뒤.

각 단계는 **별도 배포**다. RDS·Mongo·Redis 백업/복제를 전제로 한다([system-overview §4](../architecture/system-overview.md)).

### 6. 인덱스 추가 시 락 주의

- MySQL 8.0은 다수 인덱스 추가가 **online DDL**(`ALGORITHM=INPLACE, LOCK=NONE`)이나, 일부 변경(타입 변경 등)은 테이블 리빌드·락을 유발한다 — 마이그레이션에 `ALGORITHM`/`LOCK` 명시를 검토한다.
- 대형 테이블은 트래픽 낮은 시간대·복제 지연 모니터링. MVP 데이터량은 작아 영향이 작지만 규모 확장 시 재평가한다.
- community **FULLTEXT(ngram)** 인덱스는 MVP 이후 도입이며([database-design](./database-design.md) §4-7), 추가 시 동일 락 주의.

### 7. 리뷰 & 배포 흐름

1. **작성**: 새 `V__.sql`을 도메인 변경 PR에 포함. [database-design](./database-design.md) 스키마와 일치.
2. **로컬**: docker-compose MySQL에 적용 → JPA `validate` green 확인.
3. **PR/리뷰**: 마이그레이션 별도 검토(아래 체크리스트). 적용된 파일 수정 금지.
4. **CI**: 빈 DB에 전체 마이그레이션 적용 검증(Testcontainers).
5. **배포**: 앱 기동 시 적용(또는 배포 단계 `flyway migrate`). 비호환 변경은 §5로 분리.
6. **검증**: 적용 이력(`flyway_schema_history`)·기대 스키마 확인.

---

## 8. MongoDB 변경 관리

스키마리스라 DDL은 없지만 다음을 **명시적으로** 관리한다.

- **인덱스**: `2dsphere`(매물 지오)·UNIQUE(`favorites`/`recentListings`의 `(userId,listingId)`)·최신순 조회용 복합 인덱스는 **부트스트랩/마이그레이션 스크립트로 기동 시 멱등 생성**한다(이미 있으면 무시). 인덱스 정의 정본은 [database-design](./database-design.md) §4.
- **문서 구조 진화**: 컬렉션 문서에 **`schemaVersion` 필드**를 두고, 읽을 때 구버전을 신버전으로 변환(**lazy**) 또는 **배치 마이그레이션**으로 점진 이행한다.
- **초기 시드·1회성 마이그레이션 모두 Mongock changeUnit**([ADR-0032](../adr/0032-mongodb-migration-runner.md)): **최초 적재는 init changeUnit(order `0000`)이 레퍼런스 카탈로그(`diagnosisQuestions`·`diagnosisSuggestions`)를 비우고 캐노니컬 시드를 재적재**(Flyway V1 from-scratch 대응), 이후 **구조·데이터 진화는 order `0001`+ changeUnit**으로 둔다. 별도 `ApplicationRunner` 시더를 두지 않는다 — 생애주기를 Mongock 단일 메커니즘으로 통합한다. Mongock이 **자체 changelog 컬렉션에 적용 이력**을 남기고 기동 시 **미적용 changeUnit만 순서대로 1회 실행**한다(Flyway `flyway_schema_history`의 MongoDB 대응). 멀티 인스턴스 동시 기동은 Mongock **분산 락**으로 직렬화된다(자체 `_migrations`·유니크 `_id` 직렬화를 손수 구현하지 않는다). 각 `@ChangeUnit`은 **컬렉션을 소유한 모듈의 `infrastructure`**에 두어 소유권·이력을 모듈별로 유지한다(미래 MSA/DB-per-service 친화 — `common` 공유 골격 금지). Mongock은 `InitializingBean`으로 실행해 로컬 fixture·인덱스 초기화 `ApplicationRunner`보다 반드시 먼저 끝낸다. **사용자 데이터(`diagnoses`)는 컬렉션 drop 없이 영향 문서만 교체/이행**한다(레퍼런스 카탈로그 베이스라인 재적재는 예외 — 아래 파괴적 일괄 변경 금지 준수).
- **파괴적 일괄 변경 금지**([ADR-0005](../adr/0005-polyglot-persistence.md) D7): 컬렉션 전체를 멈추고 바꾸지 않고, 확장→점진 이행으로 처리한다.
- 대형 인덱스 생성은 백그라운드/복제 지연을 고려한다.

### 8-1. 매물 스키마 v4 이행 예외(1회)

[ADR-0039](../adr/0039-listing-schema-v4-registration-form.md)가 정한 **명시적 예외**다. 범위는 `listings`·`listingCatalog` 두 컬렉션의 v4 이행 **1회**로 한정하며, 다른 컬렉션·다른 변경에 확장 적용하지 않는다.

1. **listing 마이그레이션 체인을 v4 baseline으로 리셋한다.** `0099`~`0114` changeUnit을 **삭제**하고 `0115 listing-v4-baseline` 하나로 갈음한다 — [§1](#1-기본-규칙)이 인정하는 baseline 채택(`V1__baseline.sql` 대응)의 MongoDB 적용이다. v3 데이터를 폐기하는 이상 그 데이터를 v1→v2→v3로 옮기던 이력은 재현할 대상이 없다.
   - **존치: `0100`(`listing-search-place-seed`).** 유일하게 다른 컬렉션(`searchPlaces`)을 다루며 v4와 무관하다.
   - 기존 환경의 changelog에는 `0099`~`0114` 항목이 고아로 남지만, 대응 클래스가 없으면 실행 대상에서 빠질 뿐이라 무해하다.
   - **`0116 listing-location-required`는 이 예외의 연장이 아니라 일반 개정이다.** `0115`가 지오코딩이 없어 선택으로 뒀던 `location`을 필수로 조인다([ADR-0042](../adr/0042-road-address-search-with-ncp-geocoding.md)) — 시드 주입 전이라 백필 대상이 0건이어서 [§4](#4-not-null-컬럼-추가-절차)의 확장→백필→축소가 그대로 성립한다. `0115`는 동결이므로 수정하지 않고 새 유닛이 자기 스키마 사본을 든다.
   - `0115`는 **스키마만** 다룬다 — v4 validator 적용(컬렉션이 없으면 `createCollection`+validationOptions, 있으면 `collMod`)과 옛 인덱스 2건 삭제. 데이터는 건드리지 않는다. v4 `$jsonSchema`는 `0115` 안에 **동결**하고 `ListingMongoIndexInitializer`의 정적 메서드를 호출하지 않는다(과거 `0105`가 그렇게 해서 `listingV2JsonSchema()` 죽은 사본이 생겼다).
2. **시드(`listings` 2건 · `listingCatalog` 103건)는 운영자가 수동 주입한다.** 신규 필드(담당자 연락처·설문 3종 등)에 **대응하는 원본 값이 없어** 백필이 성립하지 않고, 시드를 코드가 소유하면 고칠 때마다 재빌드·재배포가 필요하다 — 위 "파괴적 일괄 변경 금지"의 1회 예외다.

   > **`--drop`을 쓰지 않는다.** 컬렉션을 지우면 **validator가 함께 사라지는데** `0115`는 1회성이라 재기동해도 다시 걸리지 않는다(부트스트랩은 인덱스만 만든다). 반드시 아래 순서를 지킨다.

   ```js
   db.listingCatalog.deleteMany({})   // drop 금지 — 컬렉션과 validator를 유지한다
   db.listings.deleteMany({})
   ```

   ```bash
   mongoimport --db kohere --collection listingCatalog --jsonArray --file kohere.listingCatalog.seed.json
   mongoimport --db kohere --collection listings       --jsonArray --file kohere.listings.seed.json
   ```

   - **신규 환경은 시드 전까지 `listingCatalog`가 비어 있다.** 그동안 매물 응답의 라벨 자리에 코드값(`SHARE_HOUSE` 등)이 그대로 나간다 — API는 실패하지 않으므로 조용히 나빠진다. 배포 절차에 시드 단계를 반드시 포함한다.
   - 이미 v4 validator가 걸린 컬렉션에 v4 문서를 넣는 것이므로 `validationLevel: off` 완화가 필요 없다. 넣기 전에 기동해도 되고, 기동 전에 넣어도 결과가 같다.
3. **인덱스 키를 바꿀 때는 새 이름으로 만든다.** 같은 이름·다른 키는 멱등 생성으로 갱신되지 않고 `IndexOptionsConflict`가 난다 — `listings_status_arc_required`(키 `status, propertyPolicies.arcRequired`)는 새 이름 `listings_status_arc_requirement`(키 `status, arcRequired`)로 만든다.
4. **인덱스 소유는 부트스트랩이 유지**한다(`ListingMongoIndexInitializer`의 멱등 생성). changeUnit이 하는 일은 **옛 인덱스 2건**(`listings_status_arc_required`·`listings_status_room_available_count`)의 **삭제뿐**이며 `0115`가 1회 수행한다.

## 9. Redis 변경 관리

- **스키마 없음**: 키스페이스·TTL은 코드 상수로 관리한다([ADR-0006](../adr/0006-refresh-token-store-redis.md), [database-design](./database-design.md) §4-1).
- **키 구조 변경**: 키 네임스페이스에 **버전**을 붙이거나(예: `refresh:v2:{tokenHash}`) **TTL 만료에 의한 자연 교체**로 이행한다. 별도 마이그레이션 도구를 두지 않는다.
- refresh 토큰은 단기 TTL이라 구조 변경 시 신 키로 발급하고 구 키는 만료로 소멸시키면 된다.

## 체크리스트

- [ ] (MySQL) `V{버전}__{설명}.sql` 네이밍·`db/migration` 배치, 버전 단조 증가
- [ ] (MySQL) 적용된(머지된) 마이그레이션 파일을 **수정하지 않았다**(forward-only·불변 — 체크섬이 근거인 Flyway 전용 항목이라 Mongock changeUnit에는 해당하지 않는다, [§1](#1-기본-규칙)·[§8-1](#8-1-매물-스키마-v4-이행-예외1회))
- [ ] 이 릴리스의 변경이 **호환(확장)** 이다 — 비호환이면 [§5 expand-contract](#5-expand-contract-패턴-무중단)로 분리했다. 점진 이행이 성립하지 않아 예외가 필요하면 [§8-1](#8-1-매물-스키마-v4-이행-예외1회)처럼 범위와 근거 ADR을 문서에 남겼다
- [ ] `NOT NULL` 추가는 [§4 절차](#4-not-null-컬럼-추가-절차)(확장→백필→축소)를 따랐다
- [ ] 대형 테이블 인덱스/타입 변경의 **락 영향**을 검토했다([§6](#6-인덱스-추가-시-락-주의))
- [ ] 스키마가 [database-design](./database-design.md) 및 도메인 엔티티와 일치하고 JPA `validate`가 green이다
- [ ] (MongoDB) 새 인덱스(`2dsphere`·UNIQUE·최신순 조회용 복합 인덱스 등)를 부트스트랩 스크립트에 **멱등** 추가했다. 기존 인덱스의 **키를 바꿀 때는 새 이름**으로 만들고(같은 이름·다른 키는 `IndexOptionsConflict`) 옛 인덱스 삭제만 changeUnit에 넣었다([§8-1](#8-1-매물-스키마-v4-이행-예외1회))
- [ ] (MongoDB) 초기 시드(init changeUnit `0000`)·이미 적재된 컬렉션의 1회성 변경 모두 컬렉션 소유 모듈의 **Mongock `@ChangeUnit`**으로 처리했다(별도 `ApplicationRunner` 시더 금지 — [§8](#8-mongodb-변경-관리)). 수동 시드로 예외 처리했다면 [§8-1](#8-1-매물-스키마-v4-이행-예외1회)의 범위에 해당하고, `--drop` 없이 `deleteMany({})` 후 import했다
- [ ] (Redis) 키 구조 변경 시 네임스페이스 버전/만료 교체 전략을 적었다
- [ ] CI에서 빈 DB 전체 적용이 통과한다

## 관련 문서

- [ADR-0008](../adr/0008-mysql-migration-flyway.md)(MySQL 마이그레이션 도구 결정) · [ADR-0032](../adr/0032-mongodb-migration-runner.md)(MongoDB=Mongock) · [ADR-0005](../adr/0005-polyglot-persistence.md)(폴리글랏) · [ADR-0006](../adr/0006-refresh-token-store-redis.md)(refresh=Redis) · [ADR-0039](../adr/0039-listing-schema-v4-registration-form.md)(매물 v4 재정의·1회 재시드 예외)
- [database-design](./database-design.md)(스키마·인덱스 정본) · [system-overview §3-2·§1-3](../architecture/system-overview.md)(스택·배포)
