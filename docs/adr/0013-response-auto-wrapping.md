# ADR-0013. 성공 응답은 ResponseBodyAdvice로 공통 래퍼를 자동 적용한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0013 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-17 |
| 관련 문서 | [ADR-0004](./0004-api-response-envelope.md), [error-response-guide](../api/error-response-guide.md), [code-style §5](../convention/code-style.md) |

## Status

Accepted

> [ADR-0004](./0004-api-response-envelope.md)는 후속으로 "성공 응답 자동 래핑(`ResponseBodyAdvice`) 적용 여부 결정"을 남겼다. 본 ADR이 그 후속을 닫는다 — **자동 래핑을 채택한다.**

## Context

- [ADR-0004](./0004-api-response-envelope.md): 모든 응답을 공통 래퍼 `{ success, data, error }`로 표준화한다. 성공 응답을 컨트롤러마다 `ApiResponse.success(...)`로 직접 감싸면 **보일러플레이트와 누락 위험**이 생긴다.
- 에러 응답은 이미 `@RestControllerAdvice` 전역 핸들러가 일괄 래핑한다([error-response-guide §5](../api/error-response-guide.md)). 성공 응답만 비대칭으로 수동이면 일관성이 깨진다.

## Decision

**`common`에 `ResponseBodyAdvice<Object>`를 구현해 컨트롤러 반환값을 `ApiResponse.success(body)`로 자동 래핑한다.**

1. 컨트롤러는 **도메인 응답 DTO만 반환**하고 래핑 코드를 두지 않는다.
2. **제외 규칙**: (a) 이미 `ApiResponse`면 재래핑하지 않는다, (b) `204 No Content` 등 본문 없는 응답, (c) `byte[]`/`Resource`/`String` 등 비-JSON 본문, (d) Spring REST Docs·Actuator·에러 응답(전역 핸들러 소관)은 제외.
3. **적용 범위**: `supports()`에서 `/api/v1` 컨트롤러(패키지/애너테이션 기준)로 한정한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 |
|---|---|---|---|
| **A. `ResponseBodyAdvice` 자동 래핑** | 컨트롤러 간결, 누락 0, MVC 메시지 컨버터 단계에 적합 | 변환이 암묵적 → 제외 규칙 명확화 필요 | **채택** |
| B. 컨트롤러 수동 래핑 | 명시적 | 보일러플레이트·누락 위험 | 미채택 |
| C. AOP `@Around` | 유연 | 반환 타입 처리·메시지 컨버터와의 단계 차이로 복잡 | 미채택 |
| D. 래퍼 미사용 | 가장 단순 | [ADR-0004](./0004-api-response-envelope.md) 위배 | 미채택 |

## Consequences

- **긍정**: 컨트롤러가 도메인 응답에 집중하고, 성공/실패 응답 래핑이 전역에서 일관된다.
- **부정/트레이드오프**: 자동 변환의 암묵성 — 제외 규칙을 분명히 하지 않으면 `String` 반환 시 `StringHttpMessageConverter` 충돌 등 함정이 생긴다.
- **후속 작업**: `ResponseBodyAdvice` 구현, [ADR-0004](./0004-api-response-envelope.md) 후속 닫음, 페이지/커서 공통 타입은 [ADR-0004](./0004-api-response-envelope.md) 별도 후속으로 유지.

## Validation

- 컨트롤러 반환값이 래퍼로 감싸져 나오는지 통합 테스트.
- 204·바이너리·이미 래핑된 응답이 **재래핑되지 않는지** 확인.
