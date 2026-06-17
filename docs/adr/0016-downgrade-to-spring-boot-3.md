# ADR-0016. Spring Boot를 4.1에서 3.5로 다운그레이드한다 (생태계·도구 호환성)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0016 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [ADR-0007](./0007-api-docs-spring-rest-docs.md), [ADR-0017](./0017-openapi-swagger-ui-from-restdocs.md), [system-overview §3](../architecture/system-overview.md), [test-strategy](../convention/test-strategy.md), [build.gradle](../../build.gradle) |

## Status

Accepted

> 초기 스택은 Spring Boot 4.1(Spring Framework 7)이었다. 일부 핵심 도구가 아직 Spring 7을 지원하지 않아, **3.5(Spring 6)** 로 내려 생태계 성숙도를 확보한다. 재상향 트리거를 함께 박아둔다.

## Context

- **Boot 4.1 = Spring Framework 7 / Spring Security 7** 로 최신이지만, 주변 라이브러리·도구가 아직 따라오지 못한 영역이 있다.
- **결정적 사례 — 테스트 기반 OpenAPI/Swagger UI**: 인터랙티브 API 문서(Swagger UI try-it-out)를 어노테이션 드리프트 없이 내려면 `restdocs-api-spec`(REST Docs 테스트 → OpenAPI)이 필요하다. 그러나 최신 `0.19.4`가 Spring 7에서 깨진다 — Spring 7에서 `HttpHeaders`가 더 이상 `Map`을 구현하지 않아 라이브러리 내부에서 `ClassCastException`(실측 확인). [ADR-0007](./0007-api-docs-spring-rest-docs.md)의 REST Docs HTML은 되지만, 브라우저 try-it-out 경로가 막힌다.
- **그 외 Spring 7 변경이 기존 코드에도 영향**: 예) `HttpStatus.UNPROCESSABLE_CONTENT`는 Spring 7 전용(6은 `UNPROCESSABLE_ENTITY`), 웹 스타터 이름(`spring-boot-starter-webmvc`↔`-web`), 테스트 MockMvc autoconfigure 패키지 이동 등.
- 정리: "최신(4.1)"의 이득보다 **도구 생태계 미성숙 비용**이 크다(특히 API 문서 도구).

## Decision

**Spring Boot 3.5.x(최신 3.x, Spring Framework 6.2 / Security 6.5)로 다운그레이드한다.**

1. Spring Boot **3.5.x**, Spring Modulith **1.4.x**.
2. 웹 스타터는 `spring-boot-starter-web`(테스트는 `spring-boot-starter-test`), HTTP 상태는 `HttpStatus.UNPROCESSABLE_ENTITY`, 테스트 MockMvc autoconfigure는 Spring 6 패키지를 쓴다.
3. 이로써 **테스트 기반 OpenAPI + Swagger UI**([ADR-0017](./0017-openapi-swagger-ui-from-restdocs.md))를 어노테이션 없이 제공할 수 있다.
4. **Testcontainers는 1.21.4 이상으로 고정**한다(Boot 3.5 기본 1.20.x의 docker-java는 신버전 Docker 데몬(예: 29.x)과 통신 시 HTTP 400 — 1.21.4의 새 docker-java가 호환). `org.testcontainers:testcontainers-bom:1.21.4`를 명시 import.
5. **재상향(Boot 4.x) 트리거**: 핵심 도구(특히 `restdocs-api-spec`)가 Spring 7을 안정 지원하면 4.x 재상향을 재검토한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. Boot 3.5 다운그레이드** | 도구 생태계 성숙, 테스트 기반 Swagger UI 가능 | 최신 Boot 4 기능 일부 포기, 다운그레이드 작업 | **채택** |
| B. Boot 4.1 유지 + 수동 OpenAPI yaml | 최신 유지 | yaml 수동 관리 → 드리프트([ADR-0007](./0007-api-docs-spring-rest-docs.md) 위배) | 미채택 |
| C. Boot 4.1 유지 + Swagger 보류 | 단순 | 브라우저 try-it-out 없음 | 미채택 |
| D. springdoc(어노테이션) | 자동 UI | 어노테이션 드리프트([ADR-0007](./0007-api-docs-spring-rest-docs.md) 반려) + Boot 4 지원 불확실 | 미채택 |

## Consequences

- **긍정**: 테스트 기반 OpenAPI/Swagger UI가 가능해지고, 서드파티 도구 호환 폭이 넓어진다(안정 버전대).
- **부정/트레이드오프**: 최신 Boot 4 기능을 못 쓰고, 추후 재상향 시 역작업이 든다. 다운그레이드 시 Spring 7 전용 API(`UNPROCESSABLE_CONTENT` 등)를 6용으로 수정해야 했다.
- **후속 작업(완료)**: [build.gradle](../../build.gradle) Boot 3.5.x / Modulith 1.4.x / Testcontainers-BOM 1.21.4 + 웹/테스트 스타터·HttpStatus 호환 수정. 문서 버전 갱신([CLAUDE.md](../../CLAUDE.md)·[system-overview](../architecture/system-overview.md)·[api-design-guide](../api/api-design-guide.md)·[code-style](../convention/code-style.md)).
- **운영 참고**: 신버전 Docker(예: 29.x)에서도 Testcontainers를 쓰려면 **TC ≥ 1.21.4** 필요(본 ADR이 고정).

## Validation

- `./gradlew build` green — 단위 + 통합(Testcontainers MySQL·Redis) 27개 테스트 통과(로컬 Docker 29.x 포함).
- `restdocs-api-spec`의 `openapi3` 생성이 Spring 6에서 정상 동작.
- **재검토 시점**: `restdocs-api-spec` 등 핵심 도구가 Spring 7을 안정 지원하면 Boot 4.x 재상향 검토.
