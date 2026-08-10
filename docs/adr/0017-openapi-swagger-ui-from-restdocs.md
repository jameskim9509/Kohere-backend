# ADR-0017. 테스트 기반 OpenAPI(restdocs-api-spec)로 Swagger UI를 서빙한다 (ADR-0007 확장)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0017 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [ADR-0007](./0007-api-docs-spring-rest-docs.md), [ADR-0016](./0016-downgrade-to-spring-boot-3.md), [ADR-0010](./0010-jwt-authentication-filter.md), [test-strategy](../convention/test-strategy.md), [system-overview §3-5](../architecture/system-overview.md), [build.gradle](../../build.gradle) |

## Status

Accepted

> [ADR-0007](./0007-api-docs-spring-rest-docs.md)은 API 문서를 테스트 기반 REST Docs로 정하고 springdoc(어노테이션)을 반려했다. 본 ADR은 그 결정의 **발행 형식**을 정한다 — 같은 REST Docs 테스트에서 OpenAPI를 생성해 **브라우저 Swagger UI(try-it-out)** 로 제공하되, 어노테이션 드리프트는 도입하지 않는다.

## Context

- REST Docs가 만드는 것은 요청/응답 **조각**이라 그대로는 읽을 수 있는 문서가 아니다. 조각을 사람이 엮는 방식(AsciiDoc)은 새 엔드포인트마다 수기 작업이 필요해 커버리지가 벌어지고, **브라우저에서 직접 호출(try-it-out)** 하는 인터랙티브 문서도 없다.
- `restdocs-api-spec`은 **동일한 REST Docs 테스트**에서 OpenAPI 3 명세를 함께 생성한다 → 어노테이션 없이 Swagger UI를 제공할 수 있고, 문서는 여전히 **테스트가 단일 소스**라 코드와 드리프트가 없다([ADR-0007](./0007-api-docs-spring-rest-docs.md)이 springdoc을 반려한 이유를 그대로 지킨다).
- 단, `restdocs-api-spec`은 Spring 6에서 동작한다 → 스택이 Boot 3.5여야 한다([ADR-0016](./0016-downgrade-to-spring-boot-3.md)).

## Decision

**REST Docs 테스트로 OpenAPI3 yaml을 생성하고, Swagger UI 정적 자산에 끼워 실행 jar에서 서빙한다.**

1. 문서 테스트는 `MockMvcRestDocumentationWrapper.document(...)`로 작성 → OpenAPI 자원(`resource.json`)을 캡처한다. 새 엔드포인트를 추가하면 `openapi3`가 자동 수집하므로 수기 조립 단계가 없다.
2. Gradle `openapi3` 태스크가 `build/api-spec/openapi3.yaml`을 생성한다(서버 URL·타이틀·버전 설정).
3. Swagger UI 정적 자산(`org.webjars:swagger-ui`)을 추출해 `static/swagger-ui/`에 번들하고, `swagger-initializer.js`가 기본 petstore 대신 **우리 `openapi3.yaml`** 을 가리키게 한다.
4. 실행 jar는 **`/swagger-ui/index.html`(Swagger UI)** 를 정적 제공한다. 이 경로는 보안 공개(permitAll, [ADR-0010](./0010-jwt-authentication-filter.md)).
5. **springdoc(어노테이션) 미도입 유지** — OpenAPI는 테스트에서만 나온다(단일 소스, 드리프트 0).

### 문서 작성 규약 (#151에서 추가)

생성기가 같은 `(path, method)` 스니펫을 **오퍼레이션 하나로 병합**하기 때문에, 스니펫 단위로 문서를 쓰면 내용이 조용히 유실된다. 아래는 실측으로 확정한 병합 규칙과 그에 대응하는 규약이다.

| 자리 | 병합 방식 | 규약 |
|---|---|---|
| `summary`·`description` | 첫 non-blank **하나**만 채택. 순서는 `FilesKt.walkTopDown()` 파일 순회 의존 | 오퍼레이션당 상수 1벌을 만들어 **성공·에러 스니펫 전부**가 동일 문자열을 쓴다 |
| `tags` | 스니펫 태그의 **union** | `com.kohere.docs.ApiDocsTags` 상수로만 부여하고 **모든 스니펫**에 붙인다. 하나만 빠뜨리면 URI 첫 세그먼트(`api`)가 섞여 두 그룹에 중복 노출된다 |
| 응답 스키마 | 합집합이 아니라 `(path, type)` 기준 **dedup·last-wins** | 같은 `(path, method, status)`를 캡처하는 스니펫은 **동일한 필드 헬퍼**를 호출한다. 파일이 달라도 마찬가지다 |
| `examples` | identifier를 키로 **전부 생존** | 역할·상태 분기는 여기서 보여준다. 케이스 구분은 summary가 아니라 **identifier**로 한다 |

#### 필드 기술

- `nullable`은 오직 `FieldDescriptor.optional()`로 만든다. `checkNullable()`이 `if (optional) nullable(true)`가 전부다.
- **`JsonFieldType.NULL`을 쓰지 않는다** — everit `NullSchema`(`{"type":"null"}`)가 swagger 역직렬화의 타입 분기에 없어 **프로퍼티가 통째로 사라진다**(실측). 공통 래퍼의 성공 `error`·실패 `data`는 `OBJECT` + `optional()`로 기술한다.
- enum은 `EnumFields`로 싣는다(상수가 바뀌면 문서가 따라가 드리프트 0). 와이어 값이 상수명과 다르거나(`lang=en|ko|ja`) 요청/응답 허용 집합이 다르면 값을 직접 나열한다.
- **배열 원소 코드값에 스칼라 `codeField`를 쓰면 안 된다** — 배열이 문자열로 잘못 문서화되는데 REST Docs 타입 검증이 건너뛰어 **테스트는 통과하고 문서만 틀린다**. `codeArrayField`(itemsType)를 쓴다.
- 필드를 `optional`로 낮출 때는 그 필드가 없어야 하는 케이스에 `jsonPath(...).doesNotExist()` 단정을 함께 추가한다. 문서가 느슨해진 만큼 단정으로 되메운다.

#### 에러 스니펫은 응답만 문서화한다

요청 예시는 `hasRequestBody`가 참일 때만 캡처된다. 그 에러에 도달하는 데 본문이 필요 없으면 — 시큐리티 필터 체인에서 나는 401·403, 본문 없음이 곧 트리거인 `MALFORMED_REQUEST` — `.content(...)`를 생략한다. `.contentType(...)`은 남긴다(지우면 415가 된다).

**status가 아니라 「어느 계층에서 던지는가」로 판정한다.** 서비스가 던지는 401·403(예: `LandlordOnlyException`, 차단 관계 403)은 유효 본문이 있어야 컨트롤러에 도달한다.

#### 자동 검증

`verifyOpenApiSpec` Gradle 태스크가 태그(미지정·`api` 유추·중복)·summary(존재·길이·개행)·description·operationId(전역 유일·접두사 붕괴)·2xx 존재를 검사한다. `openapi3`가 `check`에 의존하므로 순환을 피해 `prepareSwaggerUi`에 건다.

태그 **설명**은 넣지 않는다 — 테스트는 태그 이름만 정할 수 있고 설명은 빌드 시점 별도 파일(`tagDescriptionsPropertiesFile`)로만 넣을 수 있어, 문서의 단일 소스가 테스트라는 원칙에서 벗어난다. 이름(`ApiDocsTags`)이 자체로 설명적이고 표시 순서는 `swagger-ui-initializer.js`의 `TAG_ORDER`가 정한다.

### 알려진 한계 (라이브러리 제약, 우회 불가)

- **응답 status별 description을 쓸 수 없다** — 생성기가 `description = status.toString()`으로 하드코딩한다. Swagger의 Description 칸에 코드 숫자가 반복된다. 그래서 오퍼레이션 description 끝에 **`| status | error.code | 발생 조건 |` 표**를 두고, 이것이 status별 설명을 대신하는 유일한 자리다. 스키마 enum은 코드 **목록**만 주므로 발생 조건은 표에만 있다 — 같은 내용을 본문 산문에 중복해 적지 않는다.
- **역할별 응답 스키마 분기(`oneOf`/`discriminator`)를 만들 수 없다.** Schema 탭은 모든 역할의 **합집합** 하나뿐이다. 역할 전용 필드를 `optional`로 표시하고 실제 형태는 Examples로 보여준다.
- **요청 본문 예시가 status로 분리되지 않는다** — `contentType`으로만 묶인다. 그래서 「에러 스니펫은 응답만 문서화한다」로 개수를 줄인다.

이 세 가지는 `info.description`(`build.gradle`의 `openapi3 { description }`)에 「이 문서를 읽는 법」으로 명시한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. restdocs-api-spec + webjar Swagger UI** | 테스트 주도(드리프트 0), try-it-out 제공, 스니펫 자동 수집 | 빌드 배선 필요, Spring 6 필요([ADR-0016](./0016-downgrade-to-spring-boot-3.md)) | **채택** |
| B. springdoc(어노테이션) | 자동 생성·UI 내장 | 어노테이션↔코드 드리프트([ADR-0007](./0007-api-docs-spring-rest-docs.md) 반려) | 미채택 |
| C. 수동 openapi.yaml + Swagger UI | 단순 | yaml 수동 관리 → 드리프트 | 미채택 |
| D. AsciiDoc HTML 렌더 | 드리프트 0, 서사 서술 가능 | 조각을 **수기로 엮어야** 해 새 엔드포인트가 누락된다. try-it-out 없음 | 미채택 |

## Consequences

- **긍정**: 인터랙티브 API 문서(Swagger UI try-it-out)와 무드리프트(테스트 단일 소스)를 동시에 얻는다. 조립이 자동이라 새 엔드포인트가 누락되지 않는다.
- **부정/트레이드오프**: `restdocs-api-spec`이 Spring 7 미지원이라 Boot 3.x에 묶인다([ADR-0016](./0016-downgrade-to-spring-boot-3.md)와 연동). Swagger UI webjar 버전을 관리해야 한다.
- **후속 작업(완료)**: [build.gradle](../../build.gradle)에 `restdocs-api-spec` 플러그인·`openapi3`·`prepareSwaggerUi`·bootJar 번들 추가, 문서 테스트를 wrapper로 전환, `SecurityConfig`에 `/swagger-ui/**` 공개.

## Validation

- `./gradlew build` → `build/api-spec/openapi3.yaml`(엔드포인트 경로·오퍼레이션 포함) 생성 + 실행 jar의 `BOOT-INF/classes/static/swagger-ui`에 번들 확인.
- 배포 후 `/swagger-ui/index.html`에서 명세 렌더 + try-it-out 동작.
- 새 엔드포인트 추가 시 문서 테스트에 `document(...)`를 더하면 Swagger UI에 자동 반영(드리프트 점검 불필요).
