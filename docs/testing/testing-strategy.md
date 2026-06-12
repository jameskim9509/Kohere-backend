# Testing Strategy & Guide

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Spring Boot 3.x / Java 17 / Gradle / Spring Data JPA / PostgreSQL / Flyway / JUnit5 + Mockito + AssertJ + Testcontainers + RestAssured / Spring Security(JWT)**
> 이 스택은 예시이며, 실제 프로젝트가 확정되면 비율/목표/도구/코드 예시를 교체하세요.

## 목적

"무엇을, 어디서, 어느 수준까지 검증할지"에 대한 팀의 합의(전략)와, 계층별 **실전 작성 방법(단위/통합/E2E/데이터)** 을 한 문서에 정의한다.

관련 문서: [backend-architecture](../architecture/backend-architecture.md) · [error-response-guide](../api/error-response-guide.md) · [database-design](../database/database-design.md)

---

## 1. 테스트 피라미드

```
            /\
           /  \        E2E (5~10%)  - 핵심 사용자 시나리오만, 느림/비쌈/취약
          /----\       Integration (20~30%) - DB/메시지/외부 어댑터 경계
         /------\      Unit (60~70%) - 도메인/비즈니스 로직, 빠르고 많이
        /--------\
```

| 계층 | 비율(예시) | 무엇을 검증 | 대표 도구 | 속도 |
| --- | --- | --- | --- | --- |
| 단위(Unit) | 60~70% | 도메인 규칙, 서비스 분기, 경계값/예외 | JUnit5, Mockito, AssertJ | 매우 빠름(ms) |
| 통합(Integration) | 20~30% | JPA 매핑, 쿼리, 트랜잭션, 어댑터 | @DataJpaTest, @WebMvcTest, @SpringBootTest, Testcontainers | 보통(초) |
| E2E | 5~10% | 사용자 시나리오 전체 흐름(HTTP→DB) | RestAssured, Testcontainers | 느림(수초~분) |

> 비율은 대략적 목표 분포입니다. **빠른 피드백을 주는 단위 테스트를 두텁게** 가져가세요.
> **안티패턴(아이스크림 콘)**: E2E가 비대하고 단위가 빈약하면 CI가 느려지고 실패 원인 파악이 어렵습니다.

---

## 2. 계층별 검증 책임 매트릭스

| 아키텍처 계층 | 주 검증 테스트 | 검증 대상 | mock/실제 |
| --- | --- | --- | --- |
| 도메인(Domain) | 단위 | 엔티티 불변식, 값 객체, 도메인 서비스 | 의존성 없음(POJO) |
| 애플리케이션(Service) | 단위 | 유스케이스 분기, 트랜잭션 흐름, 예외 변환 | Repository/Gateway는 mock |
| API(Controller) | 슬라이스 통합 | 요청 검증, 직렬화, 상태코드, 인증 | @WebMvcTest + MockMvc |
| 인프라(Repository) | 슬라이스 통합 | JPA 매핑, JPQL/QueryDSL, 제약조건 | @DataJpaTest + Testcontainers |
| 외부 연동(Adapter) | 통합 | HTTP/메시지 직렬화, 타임아웃, 재시도 | WireMock / Testcontainers |
| 전체 흐름 | E2E | 회원가입→로그인→비즈니스 시나리오 | 실제 앱 + 실제 DB(Testcontainers) |

---

## 3. 커버리지 목표 (정본)

커버리지는 "최소 안전망"이며 목표 그 자체가 아닙니다. 의미 있는 분기/예외를 덮는지가 핵심입니다.
**아래 수치가 커버리지 목표의 단일 정본**이며, [non-functional-requirements](../requirements/non-functional-requirements.md)는 이 표를 참조합니다.

| 지표 | 목표(예시) | 측정 도구 | CI 게이트 |
| --- | --- | --- | --- |
| Line Coverage(전체) | ≥ 70% | JaCoCo | warn |
| Line Coverage(도메인·서비스) | ≥ 80% | JaCoCo (패키지 룰) | **fail** |
| Branch Coverage(도메인·서비스) | ≥ 70% | JaCoCo | **fail** |
| 신규/변경 코드(diff) | ≥ 80% | JaCoCo + diff 플러그인 | **fail** |

> DTO, 설정 클래스, getter/setter, 생성 코드(QueryDSL Q타입)는 집계에서 제외하세요.

```groovy
// build.gradle — 도메인/서비스 분기 커버리지 게이트 + 제외 규칙 (발췌)
jacocoTestCoverageVerification {
    violationRules { rule {
        element = 'PACKAGE'
        includes = ['com.example.app.domain.*', 'com.example.app.application.*']
        limit { counter = 'BRANCH'; value = 'COVEREDRATIO'; minimum = 0.70 }
    } }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: ['**/Q*.class', '**/*Dto.class', '**/*Config.class', '**/*Application.class'])
        }))
    }
}
check.dependsOn jacocoTestCoverageVerification
```

---

## 4. 테스트 분리와 CI 연동

빠른 단위 테스트와 느린 통합/E2E를 `@Tag`로 분리해 로컬 피드백을 빠르게 유지합니다.

| 그룹 | 태그 | 로컬 기본 | CI 단계 |
| --- | --- | --- | --- |
| 단위 | `unit` | 항상 | 모든 PR |
| 통합 | `integration` | 선택(`./gradlew integrationTest`) | 모든 PR |
| E2E | `e2e` | 거의 안 함 | main 머지/야간 |

```groovy
test { useJUnitPlatform { excludeTags 'integration', 'e2e' } }
tasks.register('integrationTest', Test) { useJUnitPlatform { includeTags 'integration' }; shouldRunAfter test }
tasks.register('e2eTest', Test) { useJUnitPlatform { includeTags 'e2e' }; shouldRunAfter integrationTest }
```

```
PR open ──> [lint] ──> [unit] ──> [integration] ──> [coverage gate] ──> ✅
main merge ───────────────────────────────────────┴──> [e2e (야간/머지)]
```

> CI 러너에서 Testcontainers를 쓰려면 Docker 데몬이 필요합니다. 환경변수/시크릿은 [security-policy](../security/security-policy.md) 기준을 따르고, 절대 테스트 코드에 하드코딩하지 마세요.

---

## 5. 무엇을 테스트하고, 무엇을 하지 않는가

| 테스트한다 | 테스트하지 않는다 |
| --- | --- |
| 도메인 규칙/불변식 (정원 초과 거부 등) | 프레임워크 자체 동작 (JPA가 save를 하는지) |
| 서비스 분기/예외 변환 | 단순 위임만 하는 패스스루 메서드 |
| 경계값/실패 케이스 | getter/setter, toString |
| 직렬화/역직렬화 계약(API contract) | 외부 라이브러리 내부 구현 |
| 트랜잭션 롤백 동작 | 로그 메시지 문자열 그 자체 |

**해피 패스만 작성하지 않는다.** 모든 기능 변경에 실패/경계/권한 케이스를 함께 덮는다.

---

## 6. 단위 테스트

외부 의존성 없이 하나의 단위(클래스/메서드) 로직을 **빠르고 결정적으로** 검증한다. Given-When-Then 구조 + AssertJ.

**무엇을 mock하나** — "내가 만든 정책/로직"은 실제로, "내 경계 밖 협력자"는 mock.

| 대상 | mock | 이유 |
| --- | --- | --- |
| Repository/Gateway/외부 클라이언트 | **mock** | 느리고 비결정적, 단위 경계 밖 |
| 도메인 엔티티/값 객체, 순수 유틸 | 안 함 | 실제 객체로 검증해야 의미 있음 |
| `Clock`/ID 생성기 등 비결정 요소 | **고정값 주입** | 결정성 확보 |
| 테스트 대상(SUT) 자신 | 절대 안 함 | mock하면 검증 의미 없음 |

```java
// 도메인 단위 — 의존성 없음
@Test @DisplayName("정원이 가득 차면 참여 시 MeetingFullException을 던진다")
void join_whenCapacityExceeded_throws() {
    Meeting meeting = Meeting.create("주말 등산", 1);
    meeting.join(memberId(1L));
    assertThatThrownBy(() -> meeting.join(memberId(2L)))
        .isInstanceOf(MeetingFullException.class).hasMessageContaining("정원");
}

// 서비스 단위 — 협력자만 mock(BDDMockito), 분기/예외 변환 검증
@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {
    @Mock MeetingRepository meetingRepository;
    @InjectMocks MeetingService meetingService;

    @Test void join_whenMeetingNotFound_throwsNotFound() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> meetingService.join(999L, 1L))
            .isInstanceOf(MeetingNotFoundException.class);
        then(meetingRepository).should(never()).save(any());
    }
}
```

**경계값/비결정 요소** — 파라미터화 테스트로 경계를 덮고, 시간/랜덤은 주입한다.

```java
@ParameterizedTest @ValueSource(ints = {0, -1, -100})
void create_whenCapacityNotPositive_throws(int capacity) {
    assertThatThrownBy(() -> Meeting.create("테스트", capacity)).isInstanceOf(IllegalArgumentException.class);
}

// 시간: 고정 Clock 주입 → 만료 검증이 결정적. 절대 Instant.now()/new Random()을 SUT 안에서 직접 호출하지 않는다.
private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
```

> 안티패턴: 한 테스트에서 여러 동작 검증 / SUT를 mock / `verify`로 모든 호출 검증(구현 결합) / `assertNotNull`만 단언.

---

## 7. 통합 테스트

여러 구성요소가 **실제로 함께 동작**하는지 검증한다. **가장 좁은 슬라이스부터** 고려한다(@DataJpaTest/@WebMvcTest → @SpringBootTest).

**Testcontainers 공통 베이스** — H2로 PostgreSQL을 흉내내지 말고 운영과 동일 엔진을 컨테이너로 띄운다. `static` 컨테이너 1개를 모든 테스트가 재사용한다.

```java
@Testcontainers
public abstract class AbstractPostgresContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app_test").withUsername("test").withPassword("test") // 가짜 값
            .withReuse(true);   // 로컬 재사용으로 속도 향상(CI는 보통 비활성)

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // Flyway가 스키마 생성, JPA는 검증만
    }
}
```

```java
// Repository 슬라이스 — Replace.NONE으로 자동 DB 교체를 끄고 실제 컨테이너 사용
@Tag("integration") @DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeetingRepositoryTest extends AbstractPostgresContainerTest {
    @Autowired MeetingRepository repo; @Autowired TestEntityManager em;

    @Test void save_whenDuplicateInviteCode_throws() {
        repo.saveAndFlush(MeetingFixture.builder().inviteCode("INV-001").build());
        assertThatThrownBy(() -> repo.saveAndFlush(MeetingFixture.builder().inviteCode("INV-001").build()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    // 조회 검증 시 em.flush(); em.clear(); 로 1차 캐시를 비워야 실제 SELECT가 검증됨
}

// Controller 슬라이스 — DB 없이 검증/직렬화/상태코드/보안. 서비스는 @MockBean
@Tag("integration") @WebMvcTest(MeetingController.class)
class MeetingControllerTest {
    @Autowired MockMvc mockMvc; @MockBean MeetingService meetingService;

    @Test @WithMockUser void create_whenCapacityInvalid_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"capacity\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))        // 에러 응답 계약(평면 스키마)
            .andExpect(jsonPath("$.errors[0].field").value("capacity"));
    }
}
```

- **@SpringBootTest 전 구간**: 트랜잭션 롤백 동작은 `@Transactional`(자동 롤백)에 의존하지 말고 **실제 커밋 후 재조회**로 검증한다.
- **외부 연동**: 실제 호출 대신 **WireMock**으로 격리하고 직렬화/타임아웃/재시도를 검증한다(`stubFor(... withStatus(503))` → 재시도 후 예외 변환 + `verify(moreThanOrExactly(2), ...)`).
- 에러 응답 형태(`code`, `errors[]`)는 [error-response-guide](../api/error-response-guide.md) 표준 계약과 일치해야 한다.

---

## 8. E2E 테스트

실제 사용자 관점에서 **전체 흐름(HTTP→인증→로직→DB)** 이 끝까지 동작하는지 검증한다. 느리고 비싸므로 **"깨지면 서비스가 안 되는" 1~2개 핵심 시나리오만**.

```java
@Tag("e2e") @SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class AbstractE2ETest extends AbstractPostgresContainerTest {  // §7 베이스 재사용
    @LocalServerPort int port;
    @BeforeEach void setUp() { RestAssured.port = port; RestAssured.basePath = "/api/v1"; }
}

// 핵심 여정을 순서대로 한 흐름으로: 가입 → 로그인 → 모임 생성 → 참여 → 상태 검증
@Test void signup_login_createMeeting_join_endToEnd() {
    signUp("host@example.com", "Passw0rd!"); signUp("guest@example.com", "Passw0rd!");
    String hostToken = login("host@example.com", "Passw0rd!");
    long meetingId = given().header("Authorization", "Bearer " + hostToken).contentType(JSON)
            .body(Map.of("title", "주말 등산", "capacity", 2))
        .when().post("/meetings")
        .then().statusCode(201).extract().jsonPath().getLong("id");
    // ... 참여자 로그인 → 참여(200) → 최종 조회로 participantCount/full 검증
}

// 실패 시나리오도 최소 1개 — 상태코드 + 에러 코드 계약
@Test void join_whenFull_returns409() {
    // ... 정원 1 모임을 채운 뒤 추가 참여
    .then().statusCode(409).body("code", equalTo("MEETING_FULL"));
}
```

- 외부 시스템은 실제 호출하지 않고 **WireMock**으로 가짜 응답을 둔다.
- 데이터 격리: 시나리오마다 **고유 식별자**(`prefix + UUID + @example.com`)를 쓰고, 가능하면 **API를 통해 셋업**(가입/로그인)해 실제 흐름을 그대로 사용한다.
- (참고) 배포 환경 대상 부하/브라우저 E2E는 **k6/Playwright** 등 별도 도구로 분리한다(시크릿은 환경변수 주입).

---

## 9. 테스트 데이터

테스트 객체를 어떻게 **만들고·격리하고·정리**할지 표준화해 셋업 중복과 flaky를 줄인다.

| 패턴 | 정의 | 언제 |
| --- | --- | --- |
| **Builder** | 합리적 기본값을 채우고 신경 쓰는 필드만 변경 | 가장 범용 |
| **Object Mother** | 의미 있는 이름의 완성 표본(`aMember()`, `aFullMeeting()`) | 자주 쓰는 전형 객체 |
| **Persistence Fixture** | DB에 미리 적재하는 헬퍼 | @DataJpaTest/@SpringBootTest |

```java
// Builder: 기본값 + 변경 필드만
Meeting full = MeetingFixture.builder().capacity(2).participants(2).build();

// Object Mother: 의도가 이름에 드러남
Member member = MemberMother.aDeactivatedMember();   // 비활성 회원(경계 케이스)
```

**격리/정리** — 테스트 간 데이터가 새면 순서 의존 flaky가 생긴다. 전략을 명시한다.

| 전략 | 방법 | 비고 |
| --- | --- | --- |
| 트랜잭션 롤백 | 클래스에 `@Transactional` | 빠름 / 실제 커밋 동작 검증 불가 |
| 명시적 정리 | `@AfterEach` `deleteAllInBatch`(FK 역순) | 커밋 검증 가능 / 순서 주의 |
| TRUNCATE | `TRUNCATE ... RESTART IDENTITY CASCADE` | 빠르고 확실 / 시퀀스 리셋 |

**고정 vs 랜덤**: **단언에 쓰는 값은 항상 고정**, UNIQUE 충돌 회피용 값만 유니크(`prefix + seq + @example.com`). 랜덤이 필요하면 **고정 seed**(`new Random(42)`)로 재현 가능하게.

> 비밀/민감 데이터는 명백한 가짜 값만(`user@example.com`, `Passw0rd!`, `<YOUR_VALUE>`). 자격증명은 환경변수/시크릿 주입([security-policy](../security/security-policy.md)).

---

## 10. 안정성(Flaky 방지) 원칙

- 시간 의존 로직은 `Clock`을 주입해 고정값으로 테스트한다.
- 랜덤 값은 고정 seed 또는 명시적 입력으로 대체한다.
- 테스트 간 데이터는 격리한다(트랜잭션 롤백 또는 컨테이너 재사용+정리).
- `Thread.sleep` 대신 Awaitility 등 폴링 기반 대기를 쓴다.
- 테스트 순서에 의존하지 않는다(`@TestMethodOrder` 남용 금지).

---

## 체크리스트

- [ ] 피라미드 비율/커버리지 목표/CI 게이트(fail 조건)를 팀과 합의했다
- [ ] 단위/통합/E2E를 `@Tag`로 분리하고 Gradle 태스크를 구성했다
- [ ] 단위: GWT 구조 + 협력자만 mock + 경계/실패 케이스 + 시간/랜덤 주입
- [ ] 통합: 운영과 동일 DB 엔진을 Testcontainers로(가장 좁은 슬라이스 우선)
- [ ] E2E: 핵심 여정 1~2개 + 실패 1개, 외부는 WireMock 격리
- [ ] 테스트 데이터: Builder/Mother + 격리·정리 + 고정/유니크 값 전략
- [ ] Flaky 방지 원칙(Clock/seed/격리)을 반영했다
- [ ] 비밀/민감 데이터 대신 가짜 값을 사용했다
- [ ] 프로젝트 확정 후 실제 스택 값으로 본 문서를 갱신했다
