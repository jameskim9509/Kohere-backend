# Test Data Guide

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Java 17 / JUnit5 / Spring Data JPA / PostgreSQL(Testcontainers)**
> 패턴(Fixture / Object Mother / Builder)은 언어·프레임워크와 무관하게 적용 가능합니다. 실제 라이브러리는 프로젝트에 맞게 교체하세요.

## 목적

테스트 데이터 가이드는 **테스트 객체를 어떻게 만들고, 격리하고, 정리할지**를 표준화합니다.
중복된 셋업 코드를 줄이고, 테스트가 깨지기 쉬운(flaky) 원인을 제거합니다.

관련 문서: [unit-test-guide](./unit-test-guide.md) · [integration-test-guide](./integration-test-guide.md) · [e2e-test-guide](./e2e-test-guide.md) · [testing-strategy](./testing-strategy.md)

---

## 1. 세 가지 패턴 비교

| 패턴 | 한 줄 정의 | 언제 쓰나 |
| --- | --- | --- |
| **Builder** | 필드를 선택적으로 채워 객체를 조립 | 일부 필드만 바꾸고 싶을 때(가장 범용) |
| **Object Mother** | 의미 있는 이름의 "완성된 표본"을 제공 | 자주 쓰는 전형적 객체(`aMember()`, `aFullMeeting()`) |
| **Fixture(통합)** | DB에 미리 적재된 데이터 셋업 | @DataJpaTest/@SpringBootTest에서 영속 상태 준비 |

> 실무에서는 **Object Mother가 내부적으로 Builder를 사용**하는 조합이 가장 흔합니다.

---

## 2. Builder 패턴

핵심 아이디어: **합리적 기본값을 채워두고, 테스트가 신경 쓰는 필드만 바꾼다.**

```java
public class MeetingFixture {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // 합리적 기본값 — 대부분의 테스트가 그대로 사용
        private String title    = "기본 모임";
        private int    capacity = 5;
        private Long   hostId   = 1L;
        private String inviteCode = "INV-DEFAULT";
        private int    currentParticipants = 0;

        public Builder title(String v)        { this.title = v; return this; }
        public Builder capacity(int v)        { this.capacity = v; return this; }
        public Builder hostId(Long v)         { this.hostId = v; return this; }
        public Builder inviteCode(String v)   { this.inviteCode = v; return this; }
        public Builder participants(int v)    { this.currentParticipants = v; return this; }

        public Meeting build() {
            Meeting m = Meeting.create(title, capacity, hostId, inviteCode);
            for (int i = 0; i < currentParticipants; i++) {
                m.join((long) (1000 + i)); // 테스트용 임의 참여자 id
            }
            return m;
        }
    }
}
```

사용:

```java
// 정원이 가득 찬 모임이 필요할 때 — 신경 쓰는 필드만 명시
Meeting full = MeetingFixture.builder().capacity(2).participants(2).build();
assertThat(full.isFull()).isTrue();
```

---

## 3. Object Mother 패턴

자주 쓰는 "표본 객체"에 **의미 있는 이름**을 부여합니다. 테스트 의도가 이름으로 드러납니다.

```java
public class MemberMother {

    public static Member.MemberBuilder aMember() {           // 평범한 활성 회원
        return Member.builder()
            .email("user@example.com")
            .nickname("테스터")
            .status(MemberStatus.ACTIVE);
    }

    public static Member anAdmin() {                          // 관리자
        return aMember().role(Role.ADMIN).nickname("관리자").build();
    }

    public static Member aDeactivatedMember() {              // 비활성 회원 (경계 케이스)
        return aMember().status(MemberStatus.DEACTIVATED).build();
    }
}
```

```java
@Test
@DisplayName("비활성 회원은 모임에 참여할 수 없다")
void join_whenDeactivated_throws() {
    Member member = MemberMother.aDeactivatedMember();   // 의도가 이름에 드러남
    assertThatThrownBy(() -> meeting.join(member))
        .isInstanceOf(InactiveMemberException.class);
}
```

> 같은 이메일을 여러 테스트가 영속하면 UNIQUE 충돌이 납니다. **DB에 적재하는 Mother는 §6의 고유 값 전략**을 적용하세요.

---

## 4. 통합 테스트용 Persistence Fixture

DB에 미리 데이터를 적재하는 헬퍼입니다. `TestEntityManager` 또는 Repository로 영속화합니다.

```java
@TestComponent
public class MeetingTestDataLoader {

    private final MeetingRepository meetingRepository;
    private final MemberRepository  memberRepository;

    public MeetingTestDataLoader(MeetingRepository m, MemberRepository mr) {
        this.meetingRepository = m; this.memberRepository = mr;
    }

    /** 호스트 1명 + 모임 1개를 적재하고 모임 id를 반환 */
    public Long loadMeetingWithHost(String hostEmail, int capacity) {
        Member host = memberRepository.save(
            MemberMother.aMember().email(hostEmail).build());
        Meeting meeting = meetingRepository.save(
            MeetingFixture.builder().hostId(host.getId()).capacity(capacity).build());
        return meeting.getId();
    }
}
```

---

## 5. 데이터 격리와 정리

테스트 간 데이터가 새면 순서 의존 flaky가 생깁니다. 전략을 명시적으로 고릅니다.

| 전략 | 적용 | 장단점 |
| --- | --- | --- |
| 트랜잭션 롤백 | `@Transactional` (단위·슬라이스) | 빠르고 자동 / 실제 커밋 동작 검증 불가 |
| `@AfterEach` 정리 | `deleteAllInBatch` (FK 역순) | 커밋 동작 검증 가능 / 순서 주의 |
| TRUNCATE 정리 | SQL `TRUNCATE ... RESTART IDENTITY CASCADE` | 빠르고 확실 / 시퀀스 리셋 |
| 컨테이너 격리 | 테스트 그룹별 새 DB | 강한 격리 / 느림 |

### TRUNCATE 기반 공통 정리 유틸 (예시)

```java
@Component
public class DatabaseCleaner {

    @PersistenceContext private EntityManager em;

    @Transactional
    public void clear(String... tables) {
        em.flush();
        for (String table : tables) {
            em.createNativeQuery(
                "TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE").executeUpdate();
        }
    }
}
```

```java
@AfterEach
void cleanUp() {
    databaseCleaner.clear("participant", "meeting", "member"); // FK 의존 역순
}
```

---

## 6. 랜덤 vs 고정 데이터 전략

| 기준 | 고정 데이터 | 랜덤/유니크 데이터 |
| --- | --- | --- |
| 검증 대상 값(assert에 쓰는 값) | **고정** | 금지(결과가 흔들림) |
| 단순 식별자(이메일/코드 등 UNIQUE 충돌 회피) | 충돌 위험 | **유니크 권장** |
| 다양성 탐색(property-based) | 부적합 | 고정 seed로 재현 가능하게 |

### 유니크 값 생성 (충돌 회피)

```java
public class TestIds {
    private static final AtomicLong SEQ = new AtomicLong();

    public static String uniqueEmail(String prefix) {
        return prefix + "+" + SEQ.incrementAndGet() + "@example.com"; // 가짜 도메인
    }
}
```

### 결정적 랜덤 (재현 가능)

```java
// seed 고정 → 실패 시 동일 입력으로 재현 가능
private final Random random = new Random(42);
```

> 원칙: **단언에 쓰는 값은 항상 고정, UNIQUE 회피용 값만 유니크.** 순수 랜덤으로 단언하면 디버깅 불가능한 flaky가 됩니다.

---

## 7. 비밀/민감 데이터 규칙

- 실제 토큰/비밀번호/주민번호/실명/실주소를 테스트 데이터로 쓰지 않는다.
- 명백한 가짜 값만 사용: `user@example.com`, `Passw0rd!`, `<YOUR_VALUE>`, `INV-001`.
- 외부 연동 자격증명은 환경변수/시크릿으로 주입한다([secrets-management](../security/secrets-management.md)).

---

## 8. 안티패턴

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 테스트마다 `new Meeting(...)` 전체 인자 작성 | 변경에 취약, 노이즈 | Builder/Mother |
| 모든 테스트가 동일 PK/이메일을 DB 적재 | UNIQUE 충돌, flaky | 유니크 값 전략 |
| 순수 랜덤 값으로 단언 | 비결정적 실패 | 고정 값 / seed |
| 거대한 공유 SQL fixture 파일 | 의도 불명확, 결합 | 테스트별 명시적 셋업 |
| 정리 누락 | 순서 의존 | 롤백/TRUNCATE/AfterEach |

---

## 체크리스트

- [ ] Builder로 기본값을 채우고 필요한 필드만 바꾸도록 했다
- [ ] 자주 쓰는 표본은 Object Mother로 의미 있는 이름을 부여했다
- [ ] 단언에 쓰는 값은 고정, UNIQUE 회피 값만 유니크로 만들었다
- [ ] 테스트 간 데이터 격리/정리 전략을 명시했다
- [ ] 랜덤은 seed 고정으로 재현 가능하게 했다
- [ ] 비밀/민감 데이터 대신 명백한 가짜 값을 사용했다
