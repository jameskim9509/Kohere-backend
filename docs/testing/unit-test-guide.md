# Unit Test Guide

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Java 17 / JUnit5(Jupiter) / Mockito / AssertJ**
> 이 스택은 예시이며, 실제 프로젝트에 맞게 도구/네이밍 규칙을 교체하세요.

## 목적

단위 테스트는 **외부 의존성 없이 하나의 단위(클래스/메서드)의 로직**을 빠르게 검증합니다.
도메인 규칙과 서비스 분기를 두텁게 덮어, 전체 테스트 피라미드의 토대를 만듭니다.

관련 문서: [testing-strategy](./testing-strategy.md) · [test-data-guide](./test-data-guide.md) · [error-response-guide](../api/error-response-guide.md)

---

## 1. 핵심 원칙

- **빠르고 결정적이다**: DB·네트워크·파일·시간(`now()`)에 의존하지 않는다.
- **하나의 동작을 검증한다**: 한 테스트는 하나의 이유로만 실패해야 한다.
- **Given-When-Then** 구조로 의도를 드러낸다.
- **AssertJ**로 가독성 있는 단언을 작성한다.
- **무엇을 mock할지 선택적이다**: 단순 값 객체/도메인은 mock하지 않고 실제 객체를 쓴다.

---

## 2. 네이밍 규칙

테스트 메서드 이름은 "무엇을, 어떤 조건에서, 어떻게"를 드러냅니다. 한국어/영어 모두 가능하되 팀 내 일관성을 유지하세요.

| 패턴 | 예시 |
| --- | --- |
| `메서드_조건_기대결과` | `join_정원이가득차면_예외를던진다` |
| `should_기대_when_조건` (영문) | `shouldThrow_whenCapacityExceeded` |
| `@DisplayName` 한국어 서술 | `@DisplayName("정원이 가득 차면 참여 시 예외를 던진다")` |

```java
@Test
@DisplayName("정원이 가득 찬 모임에 참여하면 MeetingFullException을 던진다")
void join_whenCapacityExceeded_throwsMeetingFullException() { /* ... */ }
```

---

## 3. 무엇을 mock할 것인가

| 대상 | mock 여부 | 이유 |
| --- | --- | --- |
| Repository / Gateway / 외부 클라이언트 | **mock** | 느리고 비결정적, 단위 테스트 경계 밖 |
| 도메인 엔티티 / 값 객체 (VO) | mock 안 함 | 실제 객체로 검증해야 의미 있음 |
| 순수 유틸/계산 로직 | mock 안 함 | 실제 실행이 곧 검증 |
| `Clock`, ID 생성기 등 비결정 요소 | **고정값 주입** | 결정성 확보(시간/랜덤) |
| 테스트 대상(SUT) 자신 | 절대 mock 안 함 | mock하면 검증 의미가 없음 |

> 원칙: **"내가 만든 정책/로직"은 실제로, "내 경계 밖 협력자"는 mock**.

---

## 4. 도메인 단위 테스트 예시 (의존성 없음)

```java
class MeetingTest {

    @Test
    @DisplayName("정원 미만이면 참여자를 추가할 수 있다")
    void join_whenUnderCapacity_addsParticipant() {
        // Given
        Meeting meeting = Meeting.create("주말 등산", 2);
        meeting.join(memberId(1L));

        // When
        meeting.join(memberId(2L));

        // Then
        assertThat(meeting.participantCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("정원이 가득 차면 참여 시 MeetingFullException을 던진다")
    void join_whenCapacityExceeded_throwsMeetingFullException() {
        // Given
        Meeting meeting = Meeting.create("주말 등산", 1);
        meeting.join(memberId(1L));

        // When & Then
        assertThatThrownBy(() -> meeting.join(memberId(2L)))
            .isInstanceOf(MeetingFullException.class)
            .hasMessageContaining("정원");
    }
}
```

---

## 5. 서비스 단위 테스트 예시 (Mockito)

협력자(Repository, Gateway)는 mock으로 격리하고, 서비스의 **분기와 예외 변환**을 검증합니다.

```java
@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock  MeetingRepository meetingRepository;
    @Mock  MemberRepository  memberRepository;
    @InjectMocks MeetingService meetingService;

    @Test
    @DisplayName("존재하지 않는 모임에 참여하면 MeetingNotFoundException을 던진다")
    void join_whenMeetingNotFound_throwsNotFound() {
        // Given
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> meetingService.join(999L, 1L))
            .isInstanceOf(MeetingNotFoundException.class);

        // Repository.save가 호출되지 않았음을 검증
        then(meetingRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("정상 참여 시 참여자가 저장되고 참여 인원이 증가한다")
    void join_whenValid_savesParticipant() {
        // Given
        Meeting meeting = Meeting.create("스터디", 5);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(memberRepository.existsById(10L)).willReturn(true);

        // When
        meetingService.join(1L, 10L);

        // Then
        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        then(meetingRepository).should().save(captor.capture());
        assertThat(captor.getValue().participantCount()).isEqualTo(1);
    }
}
```

> `BDDMockito`(`given`/`then`/`willReturn`)를 쓰면 Given-When-Then 흐름과 자연스럽게 맞습니다.

---

## 6. 경계값 / 실패 케이스 (필수)

해피 패스만 작성하지 않습니다. 경계와 실패를 함께 덮으세요([testing-strategy](./testing-strategy.md) §6).

### 경계값 표 예시 — 정원 `capacity`

| 입력 | 기대 결과 |
| --- | --- |
| `capacity = 0` | 생성 시 `IllegalArgumentException` |
| `capacity = 1` | 정상, 1명까지 참여 가능 |
| `capacity = MAX(예: 100)` | 정상 |
| `capacity = 101` | 정책 위반 예외 |
| 참여 인원 = capacity | 추가 참여 시 `MeetingFullException` |

### 파라미터화 테스트

```java
@ParameterizedTest(name = "capacity={0} 이면 생성 시 예외")
@ValueSource(ints = {0, -1, -100})
@DisplayName("정원이 1 미만이면 모임을 생성할 수 없다")
void create_whenCapacityNotPositive_throws(int capacity) {
    assertThatThrownBy(() -> Meeting.create("테스트", capacity))
        .isInstanceOf(IllegalArgumentException.class);
}

@ParameterizedTest
@CsvSource({
    "1, 1, true",   // 정원=1, 현재=1 → 가득참
    "5, 4, false",  // 여유 있음
    "5, 5, true"    // 가득참
})
void isFull_returnsExpected(int capacity, int current, boolean expectedFull) {
    Meeting meeting = MeetingFixture.withParticipants(capacity, current);
    assertThat(meeting.isFull()).isEqualTo(expectedFull);
}
```

---

## 7. 시간 / 랜덤 등 비결정 요소 다루기

```java
class TokenServiceTest {

    // 고정된 Clock 주입 → 만료 검증이 결정적이 됨
    private final Clock fixedClock =
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("발급 후 30분이 지나면 토큰이 만료된 것으로 판단한다")
    void isExpired_after30min_returnsTrue() {
        TokenService service = new TokenService(fixedClock);
        AccessToken token = service.issue("user-1"); // 2026-01-01T00:00:00Z 기준

        Clock later = Clock.offset(fixedClock, Duration.ofMinutes(31));
        assertThat(service.isExpired(token, later)).isTrue();
    }
}
```

> 절대 `Instant.now()`/`new Random()`을 테스트 대상 안에서 직접 호출하지 마세요. 주입 가능하게 설계합니다.

---

## 8. 자주 하는 실수 (안티패턴)

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 한 테스트에서 여러 동작 검증 | 실패 원인 불명확 | 테스트 분리 |
| SUT를 mock | 검증 의미 없음 | 실제 객체 사용 |
| `verify`로 모든 호출 검증 | 구현에 결합, 깨지기 쉬움 | 결과(상태/반환)를 검증 |
| `assertNotNull`만 단언 | 무엇이든 통과 | 구체값/상태 단언 |
| 시간/랜덤 직접 사용 | flaky | Clock/seed 주입 |

---

## 체크리스트

- [ ] Given-When-Then 구조로 작성했다
- [ ] 협력자만 mock하고 SUT/도메인은 실제 객체를 썼다
- [ ] 해피 패스 + 경계값 + 실패 케이스를 모두 덮었다
- [ ] 시간/랜덤은 주입으로 결정적이게 만들었다
- [ ] 결과(상태/반환)를 단언했고, 불필요한 `verify`에 의존하지 않았다
- [ ] `@DisplayName`으로 의도를 명확히 했다
