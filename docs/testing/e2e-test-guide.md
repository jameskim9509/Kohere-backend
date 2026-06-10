# E2E Test Guide

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Spring Boot 3.x / RestAssured / Testcontainers(PostgreSQL) / Spring Security(JWT) / JUnit5**
> 부하/성능 E2E 예시는 **k6**, 브라우저가 필요한 경우 **Playwright**를 참고용으로 제시합니다. 실제 도구는 프로젝트에 맞게 교체하세요.

## 목적

E2E(End-to-End) 테스트는 **실제 사용자 관점에서 전체 흐름**(HTTP 요청 → 인증 → 비즈니스 로직 → DB)이 끝까지 동작하는지 검증합니다.
느리고 비싸므로, **핵심 시나리오에만** 집중합니다([testing-strategy](./testing-strategy.md) §1, 5~10%).

관련 문서: [system-overview](../architecture/system-overview.md) · [api-design-guide](../api/api-design-guide.md) · [access-control](../security/access-control.md) · [test-data-guide](./test-data-guide.md)

---

## 1. E2E로 검증할 것 / 하지 않을 것

| E2E로 검증한다 | E2E로 하지 않는다 |
| --- | --- |
| 핵심 사용자 여정(가입→로그인→핵심 기능) | 모든 입력 검증 경우의 수 (→ 단위/슬라이스) |
| 인증·인가가 흐름 전체에서 동작하는지 | 개별 쿼리 정확성 (→ @DataJpaTest) |
| 상태가 단계 간 올바르게 전이되는지 | 단순 매핑/직렬화 (→ @WebMvcTest) |
| 결제·알림 등 부수효과 트리거(가짜 외부) | 외부 시스템 내부 동작 |

> 황금 규칙: **"이 흐름이 깨지면 서비스가 안 된다"** 싶은 1~2개 시나리오만 E2E로.

---

## 2. 환경 셋업 (실제 앱 + 실제 DB)

`@SpringBootTest(webEnvironment = RANDOM_PORT)`로 실제 서버를 띄우고, RestAssured로 **HTTP를 통해** 호출합니다. DB는 Testcontainers PostgreSQL을 사용합니다([integration-test-guide](./integration-test-guide.md) §2의 베이스 클래스 재사용).

```java
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractE2ETest extends AbstractPostgresContainerTest {

    @LocalServerPort int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
    }
}
```

```
┌───────────┐   HTTP   ┌────────────────────┐        ┌───────────────────┐
│ RestAssured│ ───────▶ │ Spring Boot (실제) │ ─────▶ │ PostgreSQL        │
│ (테스트)   │ ◀─────── │ Filter→Controller  │        │ (Testcontainers)  │
└───────────┘  JSON     │ →Service→Repository│        └───────────────────┘
                        └─────────┬──────────┘
                                  │ HTTP(stub)
                                  ▼
                           ┌─────────────┐
                           │ WireMock    │ 외부 결제/알림 가짜
                           └─────────────┘
```

> 외부 시스템은 실제 호출하지 않습니다. WireMock 등으로 가짜 응답을 둡니다([external-integration](../architecture/external-integration.md)).

---

## 3. 시나리오 예시: 회원가입 → 로그인 → 모임 생성 → 참여

핵심 사용자 여정을 **순서대로 한 흐름**으로 검증합니다.

```java
@Tag("e2e")
class MeetingJourneyE2ETest extends AbstractE2ETest {

    @Test
    @DisplayName("회원가입한 사용자가 로그인 후 모임을 만들고, 다른 사용자가 참여한다")
    void signup_login_createMeeting_join_endToEnd() {
        // 1) 호스트 회원가입
        signUp("host@example.com", "Passw0rd!");        // 가짜 예시 값
        // 2) 참여자 회원가입
        signUp("guest@example.com", "Passw0rd!");

        // 3) 호스트 로그인 → 액세스 토큰 획득
        String hostToken = login("host@example.com", "Passw0rd!");

        // 4) 모임 생성 (인증 필요)
        long meetingId =
            given()
                .header("Authorization", "Bearer " + hostToken)
                .contentType(ContentType.JSON)
                .body(Map.of("title", "주말 등산", "capacity", 2))
            .when()
                .post("/meetings")
            .then()
                .statusCode(201)
                .header("Location", matchesPattern("/api/v1/meetings/\\d+"))
                .extract().jsonPath().getLong("id");

        // 5) 참여자 로그인 → 모임 참여
        String guestToken = login("guest@example.com", "Passw0rd!");
        given()
            .header("Authorization", "Bearer " + guestToken)
        .when()
            .post("/meetings/{id}/participants", meetingId)
        .then()
            .statusCode(200);

        // 6) 최종 상태 검증: 참여 인원 1명, 정원 2
        given()
            .header("Authorization", "Bearer " + hostToken)
        .when()
            .get("/meetings/{id}", meetingId)
        .then()
            .statusCode(200)
            .body("participantCount", equalTo(1))
            .body("capacity", equalTo(2))
            .body("full", equalTo(false));
    }

    // --- 흐름을 읽기 쉽게 만드는 헬퍼 (재사용) ---

    private void signUp(String email, String password) {
        given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", password))
        .when().post("/auth/signup")
        .then().statusCode(201);
    }

    private String login(String email, String password) {
        return given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", password))
        .when().post("/auth/login")
        .then().statusCode(200)
            .extract().jsonPath().getString("accessToken");
    }
}
```

### 실패 시나리오도 1개는 포함

```java
@Test
@DisplayName("정원이 가득 찬 모임에 참여하면 409 Conflict를 반환한다")
void join_whenFull_returns409() {
    String hostToken  = signUpAndLogin("host2@example.com");
    long meetingId    = createMeeting(hostToken, "정원1모임", 1);

    String firstGuest  = signUpAndLogin("g1@example.com");
    joinMeeting(firstGuest, meetingId);            // 정원 채움

    String secondGuest = signUpAndLogin("g2@example.com");
    given().header("Authorization", "Bearer " + secondGuest)
    .when().post("/meetings/{id}/participants", meetingId)
    .then().statusCode(409)
        .body("code", equalTo("MEETING_FULL"));    // 에러 응답 계약
}
```

> 상태 코드/에러 코드는 [error-response-guide](../api/error-response-guide.md)와 일치해야 합니다.

---

## 4. 검증 포인트 체크리스트

| 구분 | 검증 항목 | 예시 |
| --- | --- | --- |
| 상태 코드 | 의도한 HTTP 상태 | 201 생성, 200 조회, 409 충돌, 401/403 인증·인가 |
| 헤더 | `Location`, `Content-Type` 등 | `Location: /api/v1/meetings/100` |
| 본문 계약 | 응답 스키마/필드 | `participantCount`, `code` |
| 상태 전이 | 단계 간 데이터 변화 | 참여 후 인원 +1 |
| 인가 | 토큰 없음/타인 토큰 | 401, 403 |
| 부수효과 | 알림/이벤트 발생 | WireMock으로 호출 수 검증 |

---

## 5. 데이터 격리 / 정리

E2E는 여러 엔티티를 가로지르므로 격리가 중요합니다.

- 시나리오마다 **고유한 식별자**를 사용한다(예: 이메일에 UUID/타임스탬프 접미).
- 클래스 종료 후 또는 테스트 후 데이터를 정리한다([integration-test-guide](./integration-test-guide.md) §7).
- 가능하면 **API를 통해 셋업**(가입/로그인)해 실제 흐름을 그대로 사용한다(픽스처 직접 insert보다 현실적).

```java
private String uniqueEmail(String prefix) {
    return prefix + "+" + UUID.randomUUID() + "@example.com";
}
```

---

## 6. (참고) 별도 환경 도구 — k6 / Playwright

배포된 환경을 대상으로 하는 부하·브라우저 E2E는 별도 도구로 분리할 수 있습니다. **시크릿은 환경변수로 주입**하고 절대 코드에 넣지 않습니다([secrets-management](../security/secrets-management.md)).

### k6 부하 테스트 스니펫 (예시)

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 20, duration: '30s' };

export default function () {
  const token = __ENV.ACCESS_TOKEN; // 환경변수로 주입 (가짜 값 사용)
  const res = http.get('https://api.example.com/api/v1/meetings', {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
}
```

### Playwright (브라우저가 필요한 경우만, 예시)

```javascript
// 프런트엔드가 함께 있는 경우의 사용자 여정 검증용. 백엔드 단독이면 RestAssured로 충분.
await page.goto('https://app.example.com/login');
await page.fill('#email', 'host@example.com');
await page.fill('#password', 'Passw0rd!');
await page.click('button[type=submit]');
await expect(page).toHaveURL(/\/meetings/);
```

---

## 7. 안티패턴

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 모든 케이스를 E2E로 | 느리고 깨지기 쉬움 | 핵심만, 나머지는 하위 계층 |
| 실제 외부 시스템 호출 | 불안정/비용/부수효과 | WireMock 등 가짜 |
| `Thread.sleep`로 대기 | flaky | Awaitility 폴링 |
| 테스트 간 데이터 공유 | 순서 의존 | 고유 식별자 + 정리 |
| 토큰/비밀 하드코딩 | 보안 위반 | 환경변수, 가짜 값 |

---

## 체크리스트

- [ ] 핵심 사용자 여정 1~2개로 범위를 한정했다
- [ ] 실제 앱(RANDOM_PORT) + 실제 DB(Testcontainers)로 HTTP를 통해 검증했다
- [ ] 외부 시스템은 가짜(WireMock 등)로 격리했다
- [ ] 상태 코드/헤더/본문 계약/상태 전이/인가를 검증했다
- [ ] 실패 시나리오를 최소 1개 포함했다
- [ ] 고유 식별자 + 정리로 데이터 격리를 보장했다
- [ ] 토큰/비밀은 환경변수와 가짜 값만 사용했다
