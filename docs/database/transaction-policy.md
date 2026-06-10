# Transaction Policy

> **예시(Spring Boot 기준)**: 이 문서는 **Spring `@Transactional` / Spring Data JPA / PostgreSQL**을 가정한 *예시*입니다.
> NestJS(TypeORM/Prisma), FastAPI(SQLAlchemy), Go(database/sql) 등에서는 어노테이션/API만 다를 뿐 **트랜잭션 경계, 읽기 전용 분리, 격리수준 선택, 분산 트랜잭션 회피 원칙은 동일**합니다. 스택 확정 후 [tech-stack 규칙](../../.claude/rules/tech-stack.md)을 채우고 이 문서를 갱신하세요.

## 목적

- **트랜잭션 경계를 한 곳(서비스 레이어)에 모아** 일관성 있게 관리한다.
- 읽기/쓰기 트랜잭션, 전파(propagation), 격리수준(isolation)의 **기본값과 예외 기준**을 정한다.
- 외부 호출(HTTP/메시지)과 DB 트랜잭션을 섞을 때 생기는 **장기 트랜잭션·분산 트랜잭션 문제를 회피**하는 패턴을 제시한다.
- 동시성 충돌(중복 참여 등)을 **락 전략**으로 안전하게 처리한다.

## 내용

### 1. 트랜잭션 경계는 서비스 레이어에 둔다

```text
┌─────────────┐      ┌──────────────────────────┐      ┌──────────────┐
│ Controller  │ ───▶ │ Service  @Transactional   │ ───▶ │ Repository    │
│ (HTTP 경계) │      │ ← 트랜잭션 경계는 여기 ←  │      │ (JPA)         │
└─────────────┘      └──────────────────────────┘      └──────────────┘
   트랜잭션 X            한 메서드 = 한 유스케이스 = 한 트랜잭션      트랜잭션 X(단독)
```

규칙:

- **`@Transactional`은 서비스(애플리케이션 계층) 메서드에 붙인다.** 컨트롤러나 리포지토리에 직접 트랜잭션을 열지 않는다.
- **하나의 서비스 메서드 = 하나의 유스케이스 = 하나의 트랜잭션** 을 기본으로 한다.
- 컨트롤러에서 영속성 객체를 직접 만지지 않는다(지연 로딩 `LazyInitializationException` 방지). 경계 밖으로는 DTO를 반환한다.
- 트랜잭션 경계와 계층 책임 분리는 [backend-architecture](../architecture/backend-architecture.md), [module-boundary](../architecture/module-boundary.md)를 따른다.

### 2. 읽기 전용 트랜잭션

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 클래스 기본값: 읽기 전용
public class MeetingQueryService {

    private final MeetingRepository meetingRepository;

    // 조회 메서드는 클래스 기본값(readOnly=true)을 그대로 사용
    public MeetingDetailResponse getMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        return MeetingDetailResponse.from(meeting);
    }
}
```

- **조회 전용 서비스/메서드는 `readOnly = true`** 로 둔다.
- 이점: Hibernate가 더티 체킹(변경 감지) 스냅샷을 만들지 않아 메모리/성능 이득, 의도가 코드에 드러남, 일부 환경에선 **읽기 전용 트랜잭션을 리드 레플리카로 라우팅**할 수 있다.
- **명령(Command)과 조회(Query)를 서비스 단위로 분리**하는 것을 권장한다(`MeetingCommandService` / `MeetingQueryService`).

### 3. 쓰기 트랜잭션 예시

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)              // 기본은 읽기 전용
public class MeetingCommandService {

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;

    @Transactional                            // 쓰기 메서드에서만 readOnly 해제
    public Long join(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        meeting.ensureJoinable();             // 도메인 규칙 검증(상태/정원)

        // DB 유니크 제약(uq_participants_meeting_user)이 최종 방어선.
        // 동시 중복 요청은 제약 위반으로 잡아 의미 있는 예외로 변환한다.
        try {
            Participant participant = Participant.join(meetingId, userId);
            return participantRepository.save(participant).getId();
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyJoinedException(meetingId, userId);
        }
    }
}
```

### 4. 전파(Propagation) 기본값과 사용 기준

| 전파 옵션 | 의미 | 언제 쓰나 |
| --- | --- | --- |
| `REQUIRED` (기본) | 진행 중 트랜잭션 있으면 참여, 없으면 새로 시작 | 대부분의 경우. **기본값을 유지** |
| `REQUIRES_NEW` | 항상 새 트랜잭션(기존은 일시 중단) | 본 작업 실패와 무관히 **반드시 커밋해야 하는** 작업(예: 실패 이력/감사 로그 기록) |
| `MANDATORY` | 진행 중 트랜잭션 필수, 없으면 예외 | 반드시 외부 트랜잭션 안에서만 호출돼야 하는 내부 메서드 |
| `NESTED` | 세이브포인트 기반 부분 롤백 | 특수 케이스. 남용 금지 |
| `SUPPORTS` / `NOT_SUPPORTED` / `NEVER` | 상황별 | 거의 안 씀 |

`REQUIRES_NEW` 예시 — 본 트랜잭션이 롤백돼도 실패 로그는 남겨야 할 때:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordFailure(Long meetingId, String reason) {
    failureLogRepository.save(FailureLog.of(meetingId, reason));
}
```

> **자기호출(self-invocation) 함정**: 같은 클래스 안에서 `this.otherMethod()`로 호출하면 Spring AOP 프록시를 거치지 않아 `@Transactional`이 **무시**된다. 다른 빈으로 분리하거나, 별도 트랜잭션이 필요하면 별도 서비스로 빼낸다.

### 5. 격리수준(Isolation)

| 격리수준 | 막는 현상 | 비고 |
| --- | --- | --- |
| `READ_COMMITTED` | Dirty Read | **PostgreSQL 기본값. 우리 기본값.** |
| `REPEATABLE_READ` | + Non-repeatable Read | 같은 트랜잭션 내 반복 조회 일관성 필요 시 |
| `SERIALIZABLE` | + Phantom Read | 강한 일관성 필요한 소수 케이스. 충돌·재시도 비용 큼 |

- **기본은 `DEFAULT`(= DB 기본 = PostgreSQL의 `READ_COMMITTED`)** 를 그대로 쓴다. 격리수준을 함부로 올리지 않는다.
- 정원 초과 방지처럼 **동시성에 민감한 로직은 격리수준을 올리기보다 락이나 DB 제약으로 푼다**(§6).

### 6. 동시성: 낙관적 락 vs 비관적 락

| 전략 | 방법 | 적합한 상황 |
| --- | --- | --- |
| **낙관적 락** | `@Version` 컬럼 + 충돌 시 `OptimisticLockException` 재시도 | 충돌이 드문 일반적 수정. **기본 선택** |
| **비관적 락** | `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) | 충돌이 잦고 정확한 직렬화가 필요(예: 잔여 정원 차감) |
| **DB 제약** | 유니크/체크 제약 | 중복 방지(중복 참여 등). 가장 단순하고 견고 |

비관적 락 예시 — 정원 차감처럼 race condition이 치명적인 경우:

```java
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}

@Transactional
public void joinWithCapacityCheck(Long meetingId, Long userId) {
    Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)  // 행 잠금
        .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    meeting.increaseParticipantOrThrow();   // 정원 확인 후 +1
    participantRepository.save(Participant.join(meetingId, userId));
}
```

> 비관적 락은 잠금 보유 시간이 길수록 처리량을 떨어뜨린다. **트랜잭션을 짧게** 유지하고, 잠긴 트랜잭션 안에서 외부 호출(§7)을 하지 않는다.

### 7. 트랜잭션 안에서 외부 호출을 하지 않는다

```text
나쁜 패턴 (안티패턴): DB 트랜잭션이 외부 응답을 기다리며 길어진다
  @Transactional {
      save(order);
      paymentClient.charge(...);   // ← HTTP 호출. 느리고 실패/타임아웃 가능.
      save(receipt);               //    그동안 DB 커넥션·락을 점유 → 장기 트랜잭션
  }
```

문제: 외부 호출이 느리면 **DB 커넥션과 락을 오래 점유**한다. 외부 호출 성공 + DB 커밋 실패 시 **상태 불일치**가 생긴다. DB와 외부 시스템을 한 트랜잭션으로 묶는 **2PC(분산 트랜잭션)는 회피**가 원칙이다.

권장 패턴:

```text
1) 짧은 로컬 트랜잭션으로 의도를 먼저 커밋(예: status=PENDING)하고 트랜잭션을 닫는다.
2) 트랜잭션 밖에서 외부 호출을 한다.
3) 결과를 별도 트랜잭션으로 반영(status=DONE/FAILED).
   - 트랜잭션 커밋 후 후속 작업은 @TransactionalEventListener(AFTER_COMMIT)로 트리거.
   - 실패/재시도/멱등은 [external-integration] 가이드를 따른다.
```

```java
@Transactional
public Long placeOrder(OrderCommand cmd) {
    Order order = orderRepository.save(Order.pending(cmd));
    // 커밋 이후에만 결제 이벤트 발행 → 커밋 실패 시 외부 호출 안 함
    eventPublisher.publishEvent(new OrderPlacedEvent(order.getId()));
    return order.getId();
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlacedEvent event) {
    paymentClient.charge(event.orderId());   // 트랜잭션 밖, 별도 처리/재시도
}
```

> 여러 서비스·DB에 걸친 일관성이 필요하면 **2PC 대신 Saga(보상 트랜잭션) + 멱등성 + Outbox 패턴**을 검토한다. 외부 연동 격리·재시도·idempotency는 [external-integration](../architecture/external-integration.md)와 [backend-architecture 규칙](../../.claude/rules/backend-architecture.md)을 따른다.

### 8. 롤백 규칙

- Spring 기본: **`RuntimeException`/`Error`에서 롤백, checked exception에서는 롤백 안 함.**
- checked exception에서도 롤백하려면 `@Transactional(rollbackFor = SomeCheckedException.class)`.
- 비즈니스 예외는 가급적 **unchecked(런타임) 예외**로 설계해 기본 롤백 동작과 일치시킨다([error-response-guide](../api/error-response-guide.md) 참고).
- 트랜잭션 안에서 예외를 잡아 **삼키면(swallow) 롤백이 안 일어날 수 있다**. 잡았으면 다시 던지거나 명시적으로 처리한다.

### 9. 하지 말아야 할 것(안티패턴 요약)

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 컨트롤러/리포지토리에 `@Transactional` | 경계 분산, 일관성 깨짐 | 서비스 레이어로 모은다 |
| 트랜잭션 안 외부 HTTP 호출 | 장기 트랜잭션, 불일치 | 커밋 후 처리(AFTER_COMMIT)/이벤트 |
| 같은 클래스 self-invocation | `@Transactional` 무시 | 빈 분리 |
| 무지성 `SERIALIZABLE` | 처리량 급감, 잦은 재시도 | 락/제약으로 국소 해결 |
| 예외 삼키기(catch 후 무시) | 롤백 누락 | 재던지기/명시 처리 |
| 거대한 트랜잭션(배치 전체 1트랜잭션) | 락·메모리·롤백 비용 폭증 | 청크 단위로 분할 |

## 체크리스트

- [ ] `@Transactional`이 서비스 레이어에만 있다.
- [ ] 조회 서비스/메서드는 `readOnly = true`다.
- [ ] 동시성 민감 로직에 락(낙관적/비관적) 또는 DB 제약 전략이 정해져 있다.
- [ ] 트랜잭션 안에서 외부 시스템 호출을 하지 않는다(커밋 후 처리/이벤트 사용).
- [ ] 비즈니스 예외가 롤백 정책과 일치한다(런타임 예외 또는 `rollbackFor` 명시).
- [ ] 분산 트랜잭션(2PC)을 쓰지 않고 Saga/Outbox/멱등으로 대체했다.
- [ ] (프로젝트 확정 후) 실제 프레임워크의 트랜잭션 API에 맞게 예시를 교체했다.

## 관련 문서

- [database-design](./database-design.md)
- [migration-policy](./migration-policy.md)
- [backend-architecture](../architecture/backend-architecture.md)
- [module-boundary](../architecture/module-boundary.md)
- [error-response-guide](../api/error-response-guide.md)
- [external-integration](../architecture/external-integration.md)
- [backend-architecture 규칙](../../.claude/rules/backend-architecture.md)
