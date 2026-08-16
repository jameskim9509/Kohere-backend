# ADR-0032. MongoDB 스키마·문서 이행은 모듈별 Mongock @ChangeUnit으로 1회 적용한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0032 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-29 |
| 관련 문서 | [ADR-0008](./0008-mysql-migration-flyway.md), [ADR-0005](./0005-polyglot-persistence.md), [ADR-0001](./0001-bounded-context-module-decomposition.md), [ADR-0016](./0016-downgrade-to-spring-boot-3.md), [ADR-0028](./0028-diagnosis-questions-catalog-store.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0045](./0045-nearby-university-mapping-from-seeded-coordinates.md), [migration-policy §8](../database/migration-policy.md#8-mongodb-변경-관리), [database-design §3](../database/database-design.md), [build.gradle](../../build.gradle) |

## Status

Accepted

> [ADR-0008](./0008-mysql-migration-flyway.md)이 폴리글랏 마이그레이션 전략을 정하며 MongoDB를 "스키마리스 + `schemaVersion` + 점진(lazy/배치) 마이그레이션 + 부트스트랩 스크립트"로 규정했다. 본 ADR은 그 위에서 **이미 적재된 MongoDB 컬렉션의 스키마·문서를 어떻게 1회 자동 이행할지**를 구체화한다(ADR-0008의 MongoDB 세부 후속). 마커 기반 마이그레이션 러너가 필요하다는 결정은 그대로 두되, 그 러너를 자체 구현하지 않고 **MySQL의 Flyway에 대응하는 MongoDB 마이그레이션 도구 [Mongock]을 채택**한다.

## Context

- MySQL은 Flyway가 **적용 이력(`flyway_schema_history`)**·**잠금**으로 "안 돈 마이그레이션만 1회 자동 적용"한다([ADR-0008](./0008-mysql-migration-flyway.md)). **MongoDB엔 그 대응물이 없다** — 코드베이스엔 인덱스 이니셜라이저(매 기동 멱등 재선언)뿐이라, "이미 적재된 데이터를 정확히 1회 변경"을 표현하지 못한다.
- **그 일이 실제로 필요하다.** `$jsonSchema` validator 전이(매물 v4·좌표 필수화), 키가 바뀐 옛 인덱스 삭제, 폐기된 컬렉션 드롭, 필드 이름·형태가 바뀔 때의 기존 문서 이행 — 모두 **순서가 있고, 환경당 정확히 1회여야 하며, 멀티 인스턴스 동시 기동에서 직렬화**돼야 한다.
- **데이터 적재는 다른 문제다.** 진단 문항·퀴즈·생활 팁·매물 코드 카탈로그 같은 레퍼런스 데이터를 **코드가 들고 있으면 운영자가 라벨 한 줄 고칠 때마다 재빌드·재배포**가 필요하다. 마이그레이션이 그 적재까지 맡으면 "스키마를 바꾸는 배포"와 "문구를 고치는 운영"이 같은 릴리스 사이클에 묶인다.
- **부트스트랩 코드 표류(drift)**: 같은 "기동 시 Mongo 부트스트랩"인데 모듈마다 제각각이었다 — 인덱스 멱등 생성이 [`DiagnosisIndexInitializer`](../../src/main/java/com/kohere/diagnosis/infrastructure/DiagnosisIndexInitializer.java)는 `ApplicationRunner`·`@Profile("!test")`·`MongoOperations`·try/catch로, [`ListingMongoIndexInitializer`](../../src/main/java/com/kohere/listing/infrastructure/persistence/ListingMongoIndexInitializer.java)는 `InitializingBean`·`@ConditionalOnProperty`·`MongoTemplate`·예외처리 없음으로 트리거·조건·예외처리가 전부 달랐다. 공통 규약이 없어 사람마다 다르게 구현된 결과다.
- 따라서 1회성 마이그레이션을 **자체 러너로 손수 구현하면**(마커 컬렉션 read/write·미적용 판별·실행 순서·동시 기동 직렬화·락) 이 골격을 모듈마다 또는 공유 커널(`common`)에 또 쌓아야 한다 — 표류가 더 악화되거나, `common`에 Mongo 의존이 박힌 공유 골격이 새로 생긴다.
- **MSA/DB-per-service 지향**: 모듈러 모놀리식은 미래 서비스 분리를 위해 모듈 경계를 긋는 전략이다([ADR-0001](./0001-bounded-context-module-decomposition.md)·[ADR-0005](./0005-polyglot-persistence.md)). 마이그레이션 **소유권·이력**이 공유 골격에 묶이면 분리 시 부채가 된다 — 소유권은 컬렉션을 가진 모듈에 있어야 한다.
- 컬렉션 통째 drop 같은 **파괴적 일괄 변경은 금지**다([ADR-0005](./0005-polyglot-persistence.md) D7).
- 의존성 제약: 스택은 Spring Boot 3.5.x(Spring Framework 6.2)로 다운그레이드돼 있어([ADR-0016](./0016-downgrade-to-spring-boot-3.md)) 새 의존성은 이 베이스라인 호환을 검증해야 한다. 영속은 이미 `spring-boot-starter-data-mongodb`를 쓴다.

## Decision

**MongoDB의 스키마·인덱스·문서 구조에 대한 1회성 변경은 "마커 기반 마이그레이션 러너"로 환경당 정확히 1회 자동 적용하며, 그 러너는 자체 구현이 아니라 모듈별 [Mongock] `@ChangeUnit`으로 구현한다. 반면 레퍼런스·운영 데이터의 적재는 마이그레이션이 맡지 않고 운영자가 정본 JSON으로 주입한다.** Mongock은 MySQL의 Flyway에 대응하는 MongoDB 마이그레이션 도구다. 세부는 다음과 같다.

1. **도구 = Mongock.** Flyway가 MySQL에서 하는 일(적용 이력·미적용만 1회·잠금)을 MongoDB에서 한다. Mongock이 **자체 changelog 컬렉션(적용 이력)** 과 **분산 락**을 제공하므로, 손수 만들 `_migrations` 마커 컬렉션·`ApplicationRunner`·유니크 `_id` atomic insert 직렬화를 **모두 대체**한다(바퀴를 재발명하지 않는다). = Flyway `flyway_schema_history`의 MongoDB 대응.
2. **마이그레이션 단위 = `@ChangeUnit`.** 각 1회성 변경을 자바 클래스(`@Execution`/`@RollbackExecution`)로 작성한다. Flyway의 `V{n}__.sql` 한 파일에 대응한다. 기동 시 Mongock이 **미적용 changeUnit만** 순서대로 1회 실행하고 이력을 기록한다(이미 적용됐으면 재실행하지 않는다 — **재기동 안전**).
3. **소유권은 모듈별 독립.** 각 `@ChangeUnit`은 그 컬렉션을 소유한 모듈의 `infrastructure`에 둔다. **`common`에 공유 마커 러너 골격을 만들지 않는다** — 마이그레이션 이력·소유권을 모듈별로 유지해 미래 MSA/DB-per-service를 깨지 않는다([ADR-0001](./0001-bounded-context-module-decomposition.md)·[ADR-0005](./0005-polyglot-persistence.md)). 다른 모듈 컬렉션을 건드리는 changeUnit은 모듈 경계 위반이다.
4. **changeUnit은 스키마만 맡는다 — 데이터는 넣지 않는다.** changeUnit이 하는 일은 `$jsonSchema` validator 적용·전이, 키가 바뀐 옛 인덱스 삭제, 폐기 컬렉션 드롭, 그리고 스키마가 바뀔 때 **이미 저장된 문서를 새 모양으로 이행**하는 것이다. **레퍼런스·운영 데이터(카탈로그·원장)의 적재는 changeUnit이 아니라 운영자가 정본 JSON + `mongoimport`로 주입한다** — 시드가 jar에 고정되면 문구 한 줄에 재빌드·재배포가 필요하고, 운영이 DB에서 보강한 값을 다음 배포가 덮어쓴다. 정본 JSON은 저장소가 버전 관리하며, 절차는 [migration-policy §8-1](../database/migration-policy.md)이 정본이다. 인덱스도 changeUnit이 만들지 않는다 — 멱등 생성은 부트스트랩이 소유하고 changeUnit은 **옛 인덱스 삭제**만 한다(§5).
5. **표류 수렴.** 제각각인 인덱스 부트스트랩을 **단일 규약으로 통일**해 트리거·조건·예외처리 표류를 없앤다. 인덱스는 기동 시 멱등 생성(부트스트랩 빈), 스키마·문서 이행은 Mongock — 둘의 경계를 이 문서가 정본으로 정한다.
6. **비파괴·전진.** **사용자·운영 데이터**는 컬렉션 통째 drop이 아니라 **영향 문서만 교체/이행**한다([ADR-0005](./0005-polyglot-persistence.md) D7). 마이그레이션은 **신 스키마 코드(엔티티·enum) 이후**에 적용한다 — 일이 "구 데이터를 신 스키마로 이행"이기 때문이다. 되돌리는 마이그레이션은 두지 않는다(forward-only): 잘못된 변경은 다음 changeUnit으로 고친다.
7. **의존성·호환.** [build.gradle](../../build.gradle)에 Spring Boot 3.5(Framework 6.2)·`spring-data-mongodb` 호환 Mongock 버전을 추가하고 ADR 주석을 단다(Flyway 주석과 대칭). 버전 호환은 **추가 전 검증**한다([ADR-0016](./0016-downgrade-to-spring-boot-3.md) 원칙 — 미성숙 도구가 Boot 호환을 막는 사례 회피).
8. **애플리케이션 데이터 로더보다 먼저 실행.** Mongock의 Spring runner는 `InitializingBean`으로 설정한다. 기본 `ApplicationRunner` 방식은 인덱스 초기화기와 실행 순서가 보장되지 않아, 신 스키마 객체가 구 validator에 먼저 저장될 수 있다. 따라서 모든 미적용 changeUnit을 완료한 뒤 `ApplicationRunner`가 실행되게 한다.

현재 체인은 `listing`의 `0115`~`0118`이다 — v4 저장 계약(validator)·좌표 필수화·폐기 컬렉션 드롭·대학 좌표 원장 스키마([ADR-0045](./0045-nearby-university-mapping-from-seeded-coordinates.md)). 넷 다 스키마만 다루고 문서를 넣지 않는다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. Mongock `@ChangeUnit` (채택)** | 검증된 MongoDB 마이그레이션 도구, **적용 이력·분산 락 내장**, 코드(자바)로 관리, 모듈별 독립 소유, Flyway(MySQL)와 대칭 | 외부 의존 추가, Boot 3.5 호환 검증 필요, 학습곡선 | — (채택) |
| **B. 자체 마커 러너(`_migrations` + `ApplicationRunner`)** | 의존 0, 단순 | 마커·미적용 판별·동시기동 직렬화·락을 **직접 구현·유지**, 모듈마다/`common`에 골격이 쌓여 표류·분리 부채, 바퀴 재발명 | Mongock이 바로 이 "마커 기반 러너"를 **검증된 형태로 제공** — 손수 만들 이유가 없다 |
| **C. `common` 공유 골격(자체 추상)** | DRY | 공유 커널이 MSA 분리선 위 부채, `common`에 Mongo 의존 노출, 모듈 독립 소유에 반함 | 분리 친화성·모듈 경계와 충돌 |
| **D. 시드 적재까지 changeUnit이 맡는다** | 신규 환경이 배포만으로 즉시 정상 동작하고, 시드가 코드 리뷰를 거친다 | 시드가 jar에 고정돼 **문구 한 줄에 재빌드·재배포**, 운영이 DB에서 보강한 값을 다음 배포가 덮어씀, 카탈로그 교체가 "비우고 재적재"라 파괴적 | 스키마와 콘텐츠의 변경 주기가 다르다 — 데이터는 §4대로 운영자가 주입한다 |
| **E. Liquibase (MongoDB 확장)** | DB 독립·자동 롤백 | DSL 오버헤드, MongoDB 지원이 Mongock보다 덜 일급, MySQL은 이미 Flyway(혼용 복잡) | Mongock이 MongoDB·Spring 통합에 더 적합 |
| **F. 문서별 `schemaVersion` + lazy 변환만** | 데이터 미변경·점진 | 임시 레거시 처리 코드가 영구 잔존, validator 전이엔 부적합 | 데이터 이행엔 보완적이나 단독으론 부족(Mongock과 병행 가능) |

## Consequences

- **긍정**
  - validator 전이·인덱스 정리·문서 이행이 **배포로 자동 1회 적용**되고, 이미 적용된 것은 재실행되지 않는다(재기동 안전).
  - 마커·미적용 판별·동시기동 직렬화·락을 **직접 구현·유지하지 않는다** — Mongock의 changelog·분산 락이 대신한다.
  - **스키마와 콘텐츠의 생애주기가 분리된다.** 카탈로그 문구·좌표 수정에 배포가 필요 없고, 운영이 DB에서 고친 값이 다음 배포에 덮이지 않는다.
  - **표류 방지**: 도구가 "하는 방법"을 하나로 강제해, 모듈마다 제각각이던 부트스트랩·러너 골격이 수렴한다.
  - **모듈별 독립 소유** → 미래 MSA/DB-per-service 분리 시 각 서비스가 자기 `@ChangeUnit`만 가지면 된다.
  - Flyway(MySQL)와 **대칭**이라 폴리글랏 마이그레이션 규율이 일관된다([ADR-0008](./0008-mysql-migration-flyway.md)).
- **부정/트레이드오프**
  - **신규 환경은 시드를 주입해야 동작한다.** 컬렉션이 빈 채로 기동하므로 진단 문항·퀴즈·생활 팁·매물 카탈로그가 없다 — 앱은 뜨지만 서비스가 되지 않는다. **배포 절차에 시드 단계를 반드시 포함**한다([migration-policy §8-1](../database/migration-policy.md)).
  - 정본 JSON과 코드(enum·검증)가 **따로 움직인다** — 코드에 없는 코드값을 시드에 넣으면 런타임에야 드러난다.
  - **외부 의존성 추가** — 유지보수·보안 추적, 특히 Boot 업그레이드 시 Mongock 호환을 따라가야 한다([ADR-0016](./0016-downgrade-to-spring-boot-3.md)의 교훈).
  - Mongock 학습곡선·`@ChangeUnit` 작성 규율(1회성·전진·비파괴)이 필요하다.
- **후속 작업**
  - [build.gradle](../../build.gradle)에 Boot 3.5 호환 Mongock 의존 추가(+ ADR 주석), 버전 호환 검증.
  - 인덱스 이니셜라이저를 단일 방식으로 통일한다(표류 제거).
  - 세부 운영 규칙은 [migration-policy §8](../database/migration-policy.md#8-mongodb-변경-관리)을 정본으로 한다.

## Validation

- **재적용 안전**: 이미 적용된 changeUnit이 재기동 시 재실행되지 않음(Mongock changelog 확인)을 테스트한다.
- **1회 보장**: 멀티 인스턴스 동시 기동 시 Mongock **분산 락**으로 마이그레이션이 정확히 1회만 적용됨을 검증한다.
- **스키마 이행 정합**: 적용 후 컬렉션의 validator가 신 계약이고, 기존 문서가 그 계약을 만족하는지 검증한다.
- **경계 준수**: changeUnit이 문서를 적재하지 않는지(스키마·이행만) 리뷰에서 확인한다 — 적재는 정본 JSON 주입이다.
- **모듈 경계**: changeUnit이 자기 모듈 컬렉션만 건드려 `ApplicationModules.verify()`([ModularityTest](../../src/test/java/com/kohere/ModularityTest.java))가 green인지 확인한다.
- **호환**: Boot 3.5 빌드·테스트(CI `spotlessCheck build`)가 green인지 확인한다.
- **재검토 시점**: Mongock이 Boot 업그레이드를 막거나([ADR-0016](./0016-downgrade-to-spring-boot-3.md)류) 유지보수가 중단되면 대안(자체 러너·Liquibase)으로 재검토한다. 마이그레이션이 인덱스 멱등 생성 수준에 머무르면 도구 없는 부트스트랩으로 회귀를 검토한다.

[Mongock]: https://www.mongock.io
