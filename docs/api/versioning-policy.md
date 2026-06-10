# API Versioning Policy

> 본 문서는 API **버저닝 전략**, **호환성 정의**, **Deprecation(폐기) 절차**를 정의한다.
> 예시는 **Spring Boot 3.x + URI 버저닝(`/api/v1`)** 기준이다.
> 스택이 확정되어도 정책 자체(호환성 규칙, 폐기 기간, 헤더)는 그대로 재사용한다.

관련 문서: [api-design-guide](./api-design-guide.md) · [error-response-guide](./error-response-guide.md) · [api-convention](../convention/api-convention.md) · [adr 템플릿](../adr/0000-adr-template.md)

---

## 목적

- 클라이언트가 깨지지 않도록 **변경의 안전성**을 사전에 판단하는 기준을 제공한다.
- 어쩔 수 없는 breaking change를 **예측 가능한 절차**로 진행한다(공지 → 병행 운영 → 폐기).
- 버전 도입/폐기 결정을 [ADR](../adr/0000-adr-template.md)로 추적한다.

---

## 1. 버저닝 전략: URI 버저닝

본 저장소는 **URI 경로 버저닝**을 기본 채택한다.

```text
https://api.example.com/api/v1/meetings
                            ^^
                            major version
```

| 방식 | 예시 | 채택 여부 | 비고 |
| --- | --- | --- | --- |
| URI 경로 | `/api/v1/meetings` | **채택** | 가장 명시적, 캐시/라우팅 친화적 |
| 쿼리 파라미터 | `/api/meetings?version=1` | 미채택 | 캐시 키 오염, 누락 위험 |
| 커스텀 헤더 | `X-API-Version: 1` | 미채택 | 브라우저/디버깅 비친화적 |
| Accept 헤더 | `Accept: application/vnd.example.v1+json` | 미채택 | 표현력 높지만 운영 복잡 |

### 버전 부여 규칙

- **major 버전만** 경로에 노출한다(`v1`, `v2`). minor/patch는 경로에 넣지 않는다.
- major 버전은 **breaking change가 발생할 때만** 증가시킨다.
- non-breaking 변경은 같은 버전 안에서 누적한다(버전을 올리지 않는다).
- 동시에 운영하는 major 버전은 **최대 2개**(`vN`, `vN-1`)를 권장한다.

---

## 2. 호환성 정의: Breaking vs Non-breaking

같은 major 버전 내에서는 **Non-breaking 변경만** 허용한다.
Breaking 변경이 필요하면 새 major 버전을 만든다.

### Non-breaking (같은 버전 유지 가능) ✅

| 변경 | 설명 |
| --- | --- |
| 응답에 **새 필드 추가** | 기존 클라이언트는 무시하므로 안전 |
| **선택(optional) 요청 필드 추가** | 기본값이 있거나 없어도 동작하면 안전 |
| 새 **엔드포인트 추가** | 기존 호출에 영향 없음 |
| 새 **enum 값 추가**(클라이언트가 unknown 허용 설계 시) | 사전 합의 필요 |
| 에러 응답에 **상세 필드 추가** | 표준 스키마 유지 시 안전 |
| 성능·내부 구현 개선 | 계약 불변 |

### Breaking (새 major 버전 필요) ⛔

| 변경 | 설명 |
| --- | --- |
| 필드 **삭제/이름 변경** | 기존 클라이언트 파싱 실패 |
| 필드 **타입 변경** (`string` → `int` 등) | 역직렬화 실패 |
| **필수 요청 필드 추가** | 기존 요청이 검증 실패 |
| **URL/메서드 변경** | 라우팅 불일치 |
| 응답 **구조 변경**(래핑/중첩 변경) | 파싱 경로 깨짐 |
| **HTTP status / 에러 코드 의미 변경** | 클라이언트 분기 깨짐 |
| enum 값 **삭제/의미 변경** | 처리 로직 깨짐 |
| 인증/인가 **요구사항 강화** | 기존 호출 거부 |

> 판단이 애매하면 **Breaking으로 간주**하고 보수적으로 결정한다.
> "기존 클라이언트가 코드를 바꾸지 않아도 정상 동작하는가?" 가 핵심 질문이다.

---

## 3. Deprecation(폐기) 절차

폐기는 **공지 → 병행 운영 → 폐기**의 3단계로 진행한다.

```text
[D-day: 공지]            [병행 운영 기간: 최소 90일]              [Sunset: 폐기]
   v1 deprecated 표시  ──────────  v1, v2 동시 운영  ──────────  v1 종료(410 Gone)
   응답 헤더 추가                    마이그레이션 가이드 제공            모니터링 후 제거
```

### 3.1 단계별 작업

| 단계 | 작업 | 산출물 |
| --- | --- | --- |
| 1. 공지 | 폐기 대상·일정·대안 공지. 응답에 `Deprecation`/`Sunset` 헤더 부착 | 공지 문서, [ADR](../adr/0000-adr-template.md) |
| 2. 병행 운영 | 신구 버전 동시 서비스. 사용량 모니터링. 마이그레이션 지원 | 마이그레이션 가이드, 대시보드 |
| 3. 폐기 | 트래픽 충분히 감소 확인 후 종료. 종료 후 `410 Gone` | 종료 공지, 회고 |

### 3.2 폐기 유예 기간(권장)

| 대상 | 최소 유예 |
| --- | --- |
| 외부 공개 API | **90일 이상** |
| 내부/사내 API | **30일 이상** |
| 보안상 즉시 차단이 필요한 경우 | 별도 보안 절차(즉시 가능, 사전 공지 병행) |

### 3.3 Deprecation / Sunset 헤더

폐기 예정 엔드포인트는 응답에 표준 헤더를 추가한다.

```http
HTTP/1.1 200 OK
Deprecation: true
Sunset: Wed, 31 Dec 2026 23:59:59 GMT
Link: <https://docs.example.com/api/migration/v1-to-v2>; rel="deprecation"
Warning: 299 - "GET /api/v1/meetings is deprecated. Migrate to /api/v2/meetings by 2026-12-31."
```

| 헤더 | 의미 |
| --- | --- |
| `Deprecation` | 해당 리소스가 폐기 예정임(값 `true` 또는 폐기 시작 일시) |
| `Sunset` | 실제 서비스 종료 예정 시각(HTTP-date, RFC 8594) |
| `Link; rel="deprecation"` | 마이그레이션 가이드 링크 |
| `Warning` | 사람이 읽는 경고 메시지 |

### 3.4 폐기 이후 응답

종료된 버전 호출 시 표준 에러 형식([error-response-guide](./error-response-guide.md))으로 `410 Gone`을 반환한다.

```json
{
  "error": {
    "code": "API_VERSION_GONE",
    "message": "이 API 버전은 2026-12-31에 종료되었습니다. /api/v2를 사용하세요.",
    "status": 410,
    "traceId": "abcdef0011223344556677889900aabb",
    "timestamp": "2027-01-01T00:00:01+09:00",
    "path": "/api/v1/meetings",
    "errors": []
  }
}
```

---

## 4. 마이그레이션 가이드 예시: v1 → v2

> 새 major 버전 출시 시 아래 형식의 마이그레이션 문서를 함께 배포한다.

### 4.1 변경 요약

| 구분 | v1 | v2 | breaking? |
| --- | --- | --- | --- |
| 모임 식별자 | `meetingId` (number) | `meetingId` (UUID string) | ⛔ 타입 변경 |
| 시작 시각 필드 | `startTime` (epoch millis) | `startAt` (ISO-8601) | ⛔ 이름·타입 변경 |
| 정원 필드 | 없음 | `capacity` (number, optional) | ✅ 선택 필드 추가 |
| 상태 enum | `OPEN`/`CLOSE` | `OPEN`/`CLOSED`/`CANCELED` | ⛔ 값 변경/추가 |

### 4.2 응답 변화

```jsonc
// v1
{ "meetingId": 1024, "startTime": 1782950400000, "status": "CLOSE" }

// v2
{
  "meetingId": "8a1f2c34-5b6d-4e7f-8901-23456789abcd",
  "startAt": "2026-07-01T09:00:00+09:00",
  "status": "CLOSED",
  "capacity": 12
}
```

### 4.3 클라이언트 체크리스트

- [ ] `meetingId`를 number가 아닌 string으로 처리하도록 수정
- [ ] `startTime`(epoch) → `startAt`(ISO-8601) 파싱 로직 교체
- [ ] `status` enum 매핑에 `CLOSED`, `CANCELED` 추가, `CLOSE` 제거
- [ ] 호출 경로를 `/api/v1` → `/api/v2`로 변경
- [ ] `Sunset` 헤더 모니터링으로 잔여 v1 호출 제거 확인

---

## 5. Spring 라우팅 스케치 (예시)

> 버전별 컨트롤러를 분리해 운영한다. 공통 로직은 서비스 계층에서 공유한다.

```java
@RestController
@RequestMapping("/api/v1/meetings")
class MeetingV1Controller { /* 레거시 매핑 + Deprecation 헤더 부착 */ }

@RestController
@RequestMapping("/api/v2/meetings")
class MeetingV2Controller { /* 신규 계약 */ }
```

```java
// 폐기 예정 응답에 헤더를 일괄 부착하는 인터셉터 예시
@Component
class DeprecationHeaderInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (req.getRequestURI().startsWith("/api/v1/")) {
            res.setHeader("Deprecation", "true");
            res.setHeader("Sunset", "Wed, 31 Dec 2026 23:59:59 GMT");
            res.setHeader("Link", "<https://docs.example.com/api/migration/v1-to-v2>; rel=\"deprecation\"");
        }
        return true;
    }
}
```

---

## 6. 의사결정 기록

- 버전 도입/폐기는 [ADR](../adr/0000-adr-template.md)로 남긴다(선택한 대안 + 버린 대안).
- 폐기 일정은 공지 채널과 본 문서에 동시에 기록하고, 종료 후 회고를 추가한다.

---

## 체크리스트

- [ ] 변경이 Non-breaking인지 Breaking인지 2절 표로 판정했는가
- [ ] Breaking이면 새 major 버전(`/api/vN+1`)으로 분리했는가
- [ ] 폐기 시 `Deprecation`/`Sunset`/`Link` 헤더를 부착했는가
- [ ] 최소 유예 기간(외부 90일/내부 30일)을 확보했는가
- [ ] 마이그레이션 가이드(변경표 + 응답 diff + 체크리스트)를 제공했는가
- [ ] 종료된 버전이 `410 Gone` 표준 에러를 반환하는가
- [ ] 버전 결정을 [ADR](../adr/0000-adr-template.md)로 기록했는가
