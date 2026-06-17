# ADR-0017. 테스트 기반 OpenAPI(restdocs-api-spec)로 Swagger UI를 서빙한다 (ADR-0007 확장)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0017 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [ADR-0007](./0007-api-docs-spring-rest-docs.md), [ADR-0016](./0016-downgrade-to-spring-boot-3.md), [ADR-0010](./0010-jwt-authentication-filter.md), [test-strategy](../convention/test-strategy.md), [system-overview §3-5](../architecture/system-overview.md), [build.gradle](../../build.gradle) |

## Status

Accepted

> [ADR-0007](./0007-api-docs-spring-rest-docs.md)은 API 문서를 테스트 기반 REST Docs(HTML)로 정하고 springdoc(어노테이션)을 반려했다. 본 ADR은 그 결정을 **확장**한다 — 같은 REST Docs 테스트에서 OpenAPI를 생성해 **브라우저 Swagger UI(try-it-out)** 까지 제공하되, 어노테이션 드리프트는 도입하지 않는다.

## Context

- REST Docs HTML([ADR-0007](./0007-api-docs-spring-rest-docs.md))은 설계 정합·검증엔 좋지만 **브라우저에서 직접 호출(try-it-out)** 하는 인터랙티브 문서가 없다.
- `restdocs-api-spec`은 **동일한 REST Docs 테스트**에서 OpenAPI 3 명세를 함께 생성한다 → 어노테이션 없이 Swagger UI를 제공할 수 있고, 문서는 여전히 **테스트가 단일 소스**라 코드와 드리프트가 없다([ADR-0007](./0007-api-docs-spring-rest-docs.md)이 springdoc을 반려한 이유를 그대로 지킨다).
- 단, `restdocs-api-spec`은 Spring 6에서 동작한다 → 스택이 Boot 3.5여야 한다([ADR-0016](./0016-downgrade-to-spring-boot-3.md)).

## Decision

**REST Docs 테스트로 OpenAPI3 yaml을 생성하고, Swagger UI 정적 자산에 끼워 실행 jar에서 서빙한다.**

1. 문서 테스트는 `MockMvcRestDocumentationWrapper.document(...)`로 작성 → REST Docs HTML 스니펫 + OpenAPI 자원을 **동시 캡처**.
2. Gradle `openapi3` 태스크가 `build/api-spec/openapi3.yaml`을 생성한다(서버 URL·타이틀·버전 설정).
3. Swagger UI 정적 자산(`org.webjars:swagger-ui`)을 추출해 `static/swagger-ui/`에 번들하고, `swagger-initializer.js`가 기본 petstore 대신 **우리 `openapi3.yaml`** 을 가리키게 한다.
4. 실행 jar는 **`/docs/index.html`(REST Docs HTML)** 와 **`/swagger-ui/index.html`(Swagger UI)** 를 둘 다 정적 제공한다. 두 경로는 보안 공개(permitAll, [ADR-0010](./0010-jwt-authentication-filter.md)).
5. **springdoc(어노테이션) 미도입 유지** — OpenAPI는 테스트에서만 나온다(단일 소스, 드리프트 0).

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. restdocs-api-spec + webjar Swagger UI** | 테스트 주도(드리프트 0), try-it-out 제공, REST Docs HTML과 공존 | 빌드 배선 필요, Spring 6 필요([ADR-0016](./0016-downgrade-to-spring-boot-3.md)) | **채택** |
| B. springdoc(어노테이션) | 자동 생성·UI 내장 | 어노테이션↔코드 드리프트([ADR-0007](./0007-api-docs-spring-rest-docs.md) 반려) | 미채택 |
| C. 수동 openapi.yaml + Swagger UI | 단순 | yaml 수동 관리 → 드리프트 | 미채택 |
| D. REST Docs HTML만 | 드리프트 0 | 브라우저 try-it-out 없음 | 본 ADR이 보완 |

## Consequences

- **긍정**: 인터랙티브 API 문서(Swagger UI try-it-out)와 무드리프트(테스트 단일 소스)를 동시에 얻고, REST Docs HTML과 공존한다.
- **부정/트레이드오프**: `restdocs-api-spec`이 Spring 7 미지원이라 Boot 3.x에 묶인다([ADR-0016](./0016-downgrade-to-spring-boot-3.md)와 연동). Swagger UI webjar 버전을 관리해야 한다.
- **후속 작업(완료)**: [build.gradle](../../build.gradle)에 `restdocs-api-spec` 플러그인·`openapi3`·`prepareSwaggerUi`·bootJar 번들 추가, 문서 테스트를 wrapper로 전환, `SecurityConfig`에 `/swagger-ui/**` 공개.

## Validation

- `./gradlew build` → `build/api-spec/openapi3.yaml`(엔드포인트 경로·오퍼레이션 포함) 생성 + 실행 jar의 `BOOT-INF/classes/static/swagger-ui`에 번들 확인.
- 배포 후 `/swagger-ui/index.html`에서 명세 렌더 + try-it-out 동작.
- 새 엔드포인트 추가 시 문서 테스트에 `document(...)`를 더하면 Swagger UI에 자동 반영(드리프트 점검 불필요).
