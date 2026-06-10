# Integration Test Guide

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Spring Boot 3.x / Spring Data JPA / PostgreSQL / Flyway / Testcontainers / JUnit5**
> 이 스택은 예시이며, 실제 DB·마이그레이션 도구가 다르면 컨테이너 이미지와 설정을 교체하세요.

## 목적

통합 테스트는 **여러 구성요소가 실제로 함께 동작하는지**를 검증합니다.
특히 JPA 매핑/쿼리, 트랜잭션 경계, 컨트롤러 직렬화/검증, 외부 어댑터 경계를 실제 인프라(또는 그에 준하는 컨테이너)로 확인합니다.

관련 문서: [testing-strategy](./testing-strategy.md) · [database-design](../database/database-design.md) · [transaction-policy](../database/transaction-policy.md) · [migration-policy](../database/migration-policy.md) · [test-data-guide](./test-data-guide.md)

---

## 1. 통합 테스트 유형 한눈에

| 유형 | 애너테이션 | 로딩 범위 | 주 용도 |
| --- | --- | --- | --- |
| 슬라이스(Repo) | `@DataJpaTest` | JPA 관련 빈만 | 매핑/쿼리/제약조건 |
| 슬라이스(Web) | `@WebMvcTest` | 컨트롤러 계층만 | 요청검증/직렬화/상태코드/인증 |
| 전체 통합 | `@SpringBootTest` | 전체 컨텍스트 | 서비스+DB+트랜잭션 전 구간 |
| 외부 연동 | `@SpringBootTest` + WireMock | 앱 + 가짜 외부 | HTTP 클라이언트/재시도/타임아웃 |

> **원칙: 가장 좁은 슬라이스부터.** 굳이 전체 컨텍스트가 필요 없으면 `@DataJpaTest`/`@WebMvcTest`로 빠르게.

---

## 2. Testcontainers 공통 설정 (PostgreSQL)

H2 같은 인메모리 DB는 실제 PostgreSQL 방언/제약과 다르게 동작할 수 있습니다. **운영과 동일한 엔진을 컨테이너로** 띄웁니다.

### 의존성 (`build.gradle`)

```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
```

### 공통 베이스 클래스 (컨테이너 1개를 모든 테스트에서 재사용)

```java
@Testcontainers
public abstract class AbstractPostgresContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app_test")
            .withUsername("test")
            .withPassword("test")   // 예시용 가짜 값 — 운영 비밀과 무관
            .withReuse(true);       // 로컬에서 컨테이너 재사용으로 속도 향상

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway가 스키마를 만들고, JPA는 검증만
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
```

> `static` 컨테이너는 클래스 전체에서 한 번만 뜹니다. `withReuse(true)`는 `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` 설정 시 로컬에서 컨테이너를 재사용해 속도를 크게 높입니다(CI에서는 보통 비활성).

---

## 3. @DataJpaTest 예시 (Repository 슬라이스)

`@DataJpaTest`는 기본적으로 임베디드 DB로 교체하려 하므로, **Testcontainers를 쓸 때 자동 교체를 끕니다.**

```java
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 실제 컨테이너 DB 사용
class MeetingRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired MeetingRepository meetingRepository;
    @Autowired TestEntityManager em;

    @Test
    @DisplayName("호스트 ID로 모임 목록을 최신순으로 조회한다")
    void findByHostId_returnsLatestFirst() {
        // Given
        em.persist(MeetingFixture.builder().hostId(1L).title("A").build());
        em.persist(MeetingFixture.builder().hostId(1L).title("B").build());
        em.persist(MeetingFixture.builder().hostId(2L).title("C").build());
        em.flush();
        em.clear();

        // When
        List<Meeting> result = meetingRepository.findByHostIdOrderByCreatedAtDesc(1L);

        // Then
        assertThat(result).hasSize(2)
            .extracting(Meeting::getTitle)
            .containsExactly("B", "A");
    }

    @Test
    @DisplayName("UNIQUE 제약 위반 시 DataIntegrityViolationException이 발생한다")
    void save_whenDuplicateInviteCode_throws() {
        meetingRepository.saveAndFlush(MeetingFixture.builder().inviteCode("INV-001").build());

        assertThatThrownBy(() ->
            meetingRepository.saveAndFlush(MeetingFixture.builder().inviteCode("INV-001").build()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

> `em.flush(); em.clear();`로 1차 캐시를 비워야 **실제 SELECT 쿼리**가 검증됩니다. 제약조건은 `saveAndFlush`로 즉시 DB에 반영해 확인합니다.

---

## 4. @WebMvcTest 예시 (Controller 슬라이스)

DB를 띄우지 않고 컨트롤러의 **요청 검증, 직렬화, 상태코드, 보안**만 검증합니다. 서비스는 `@MockBean`으로 대체합니다.

```java
@Tag("integration")
@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  MeetingService meetingService;

    @Test
    @WithMockUser(username = "user-1")
    @DisplayName("유효한 요청이면 201과 Location 헤더를 반환한다")
    void create_whenValid_returns201() throws Exception {
        given(meetingService.create(any())).willReturn(100L);

        var body = """
            { "title": "주말 등산", "capacity": 5 }
            """;

        mockMvc.perform(post("/api/v1/meetings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/meetings/100"));
    }

    @Test
    @WithMockUser
    @DisplayName("정원이 0이면 400과 검증 에러 응답을 반환한다")
    void create_whenCapacityInvalid_returns400() throws Exception {
        var body = """
            { "title": "주말 등산", "capacity": 0 }
            """;

        mockMvc.perform(post("/api/v1/meetings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))   // 에러 응답 계약
            .andExpect(jsonPath("$.errors[0].field").value("capacity"));
    }

    @Test
    @DisplayName("인증되지 않은 요청이면 401을 반환한다")
    void create_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/meetings").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
```

> 에러 응답 형태(`code`, `errors[]`)는 [error-response-guide](../api/error-response-guide.md)의 표준 계약과 일치해야 합니다.

---

## 5. @SpringBootTest 예시 (서비스 + DB 전 구간 / 트랜잭션)

서비스→Repository→DB 흐름을 실제로 실행해 **트랜잭션과 데이터 정합성**을 검증합니다.

```java
@Tag("integration")
@SpringBootTest
class MeetingServiceIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired MeetingService meetingService;
    @Autowired MeetingRepository meetingRepository;
    @Autowired MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(MemberFixture.aMember().build());
        this.memberId = member.getId();
    }

    @Test
    @DisplayName("정원 초과 참여 시 예외가 나고 참여자 수는 변하지 않는다(롤백)")
    void join_whenFull_rollsBack() {
        // Given: 정원 1, 이미 1명 참여
        Long meetingId = meetingService.create(new CreateMeetingCommand("스터디", 1, memberId));
        meetingService.join(meetingId, memberId);

        // When & Then
        Long anotherMember = memberRepository.save(MemberFixture.aMember().build()).getId();
        assertThatThrownBy(() -> meetingService.join(meetingId, anotherMember))
            .isInstanceOf(MeetingFullException.class);

        // 트랜잭션이 롤백되어 참여 인원은 그대로
        Meeting found = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(found.participantCount()).isEqualTo(1);
    }
}
```

> 데이터 변경을 검증할 때는 `@Transactional`(자동 롤백)에 의존하지 말고, 위처럼 **실제 커밋 후 재조회**하는 편이 트랜잭션 동작까지 검증할 수 있습니다. 정리는 §7 참고.

---

## 6. 외부 연동 통합 테스트 (WireMock)

외부 HTTP 호출은 실제 서버 대신 WireMock으로 격리하고, **직렬화/타임아웃/재시도**를 검증합니다. (외부 연동 격리 원칙: [external-integration](../architecture/external-integration.md))

```java
@Tag("integration")
@SpringBootTest
class PushNotificationAdapterTest {

    static WireMockServer wireMock = new WireMockServer(0); // 랜덤 포트

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll  static void stop()  { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("integration.push.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired PushNotificationAdapter adapter;

    @Test
    @DisplayName("외부 서버가 503을 반환하면 재시도 후 PushException으로 변환한다")
    void send_whenServerError_throwsPushException() {
        wireMock.stubFor(post("/v1/push")
            .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.send(new PushMessage("user-1", "안녕하세요")))
            .isInstanceOf(PushException.class);

        wireMock.verify(moreThanOrExactly(2), postRequestedFor(urlEqualTo("/v1/push")));
    }
}
```

---

## 7. 데이터 셋업 / 정리 전략

| 전략 | 방법 | 장점 | 주의 |
| --- | --- | --- | --- |
| 트랜잭션 롤백 | 테스트 클래스에 `@Transactional` | 빠르고 자동 | 비동기/실제 커밋 동작은 검증 불가 |
| 명시적 정리 | `@AfterEach`에서 `deleteAll` 또는 truncate | 커밋 동작 검증 가능 | 외래키 순서 주의 |
| 스키마 truncate | SQL `TRUNCATE ... CASCADE` | 빠르고 확실 | FK 의존 순서/시퀀스 리셋 고려 |

### 명시적 정리 예시

```java
@AfterEach
void cleanUp() {
    // 외래키 의존 역순으로 삭제
    participantRepository.deleteAllInBatch();
    meetingRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
}
```

> 픽스처/빌더 패턴으로 셋업 보일러플레이트를 줄이세요 → [test-data-guide](./test-data-guide.md).
> Flyway 마이그레이션 자체의 호환성 검증은 [migration-policy](../database/migration-policy.md) 기준을 따릅니다.

---

## 8. 안티패턴

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 모든 테스트에 `@SpringBootTest` | 컨텍스트 매번 로딩, 느림 | 슬라이스 우선 |
| H2로 PostgreSQL 흉내 | 방언/제약 불일치로 거짓 통과 | Testcontainers(동일 엔진) |
| 테스트마다 컨테이너 생성 | 매우 느림 | static 컨테이너 + reuse |
| 1차 캐시 미초기화로 쿼리 미검증 | 실제 SQL이 안 돌아감 | `flush()`+`clear()` |
| 테스트 간 데이터 누수 | 순서 의존 flaky | 격리/정리 명시 |

---

## 체크리스트

- [ ] 가능한 가장 좁은 슬라이스(@DataJpaTest/@WebMvcTest)를 먼저 고려했다
- [ ] 운영과 동일한 DB 엔진을 Testcontainers로 사용했다
- [ ] static 컨테이너 + (로컬) reuse로 속도를 확보했다
- [ ] `@DataJpaTest`에서 `Replace.NONE`으로 자동 DB 교체를 껐다
- [ ] 데이터 셋업/정리 전략을 정하고 테스트 간 격리를 보장했다
- [ ] 트랜잭션 롤백 동작은 실제 커밋 후 재조회로 검증했다
- [ ] 외부 연동은 WireMock 등으로 격리하고 실패/재시도를 검증했다
