# Code Style Convention

> **예시(Spring Boot 기준) 안내**
> 이 문서의 코드/패키지 예시는 **Spring Boot 3.x / Java 17 / Gradle / Lombok** 기준의 **예시**입니다.
> 다른 언어/프레임워크(NestJS, FastAPI, Go 등)로 확정되면 *원칙*은 유지하고 *문법 예시*는 해당 언어의 표준 스타일 가이드로 교체하세요.
> 관련 규칙: [.claude/rules/code-style.md](../../.claude/rules/code-style.md), [.claude/rules/backend-architecture.md](../../.claude/rules/backend-architecture.md)

## 목적

읽기 쉽고 유지보수 가능한 코드를 위해 네이밍, 포맷, 패키지 구조, Lombok/null 처리 기준을 통일한다.
포맷 논쟁을 도구로 자동화해 리뷰는 **설계와 로직**에 집중하게 한다.

---

## 1. 포맷팅 / 자동화 도구 (예시)

| 항목 | 도구(예시) | 기준 |
| --- | --- | --- |
| 코드 포맷터 | Spotless (google-java-format) | 빌드 시 자동 검사 |
| 정적 분석 | Checkstyle, SpotBugs | 경고를 빌드 실패로 격상 검토 |
| 들여쓰기 | 공백 4칸 | 탭 금지 |
| 한 줄 길이 | 120자 권장 | 넘으면 줄바꿈 |
| import | 와일드카드(`import a.b.*`) 금지 | 명시적 import |
| 파일 인코딩 / 개행 | UTF-8 / LF | `.editorconfig`로 강제 |

```bash
# 예시 명령 (프로젝트 확정 후 활성화)
./gradlew spotlessApply   # 포맷 자동 적용
./gradlew spotlessCheck   # CI 검사
./gradlew check           # 정적 분석 포함
```

> 공통 검증은 현재 [scripts/quality/check.sh](../../scripts/quality/check.sh)로 수행한다(CLAUDE.md 참조).

---

## 2. 네이밍 규칙

| 대상 | 규칙 | 좋은 예 | 나쁜 예 |
| --- | --- | --- | --- |
| 클래스/인터페이스 | PascalCase | `OrderService` | `orderservice`, `Order_Service` |
| 메서드/변수 | camelCase | `findActiveUsers()` | `FindActiveUsers()`, `find_active_users()` |
| 상수 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` | `maxPageSize`(상수일 때) |
| 패키지 | 소문자, 단수 도메인 | `com.example.order` | `com.example.Orders` |
| 불리언 메서드 | `is`/`has`/`can` prefix | `isActive()`, `hasPermission()` | `active()`, `permission()` |
| 테스트 메서드 | 행위_조건_결과 | `cancel_whenAlreadyPaid_throws()` | `test1()` |
| DTO | 의도 접미사 | `CreateUserRequest`, `UserResponse` | `UserDto`(역할 불명확) |

---

## 3. 패키지 구조 (예시)

도메인형(package-by-feature)을 기본으로 한다. 계층형(package-by-layer)보다 응집도가 높다.

```text
com.example.app
├── common            # 공통 응답, 예외, 유틸 (cross-cutting)
│   ├── error         # ErrorResponse, GlobalExceptionHandler
│   └── config        # SecurityConfig, JpaConfig 등
├── user              # 사용자 도메인
│   ├── api           # UserController (API 계층)
│   ├── application   # UserService (애플리케이션/유스케이스 계층)
│   ├── domain        # User, UserRepository(interface) (도메인 계층)
│   └── infra         # UserJpaRepository, 외부 어댑터 (인프라 계층)
└── order             # 주문 도메인
    ├── api
    ├── application
    ├── domain
    └── infra
```

- 계층 책임은 [.claude/rules/backend-architecture.md](../../.claude/rules/backend-architecture.md)를 따른다(API/애플리케이션/도메인/인프라 분리).
- 의존 방향은 **api → application → domain ← infra**. 도메인은 프레임워크/인프라에 의존하지 않는다.

---

## 4. Lombok 사용 규칙

| 권장 | 지양/금지 | 이유 |
| --- | --- | --- |
| `@Getter` | `@Setter` (특히 Entity) | 무분별한 가변성 → 불변/캡슐화 유지 |
| `@RequiredArgsConstructor` (생성자 주입) | `@Autowired` 필드 주입 | 테스트 용이, 불변 필드 |
| `@Builder` (필드 많은 객체) | `@Data` (Entity) | `@Data`는 `equals`/`hashCode`/`Setter`까지 노출 |
| `@Value` (불변 DTO) | 의미 없는 `@AllArgsConstructor` 남발 | 의도 드러내기 |

```java
// 좋은 예: 생성자 주입 + 불변 필드 + 명시적 도메인 메서드
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel(); // 상태 전이는 도메인 메서드 내부에서
    }
}
```

```java
// 나쁜 예: 필드 주입 + Setter 노출 + 서비스가 상태를 직접 변경
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository; // 필드 주입

    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId).get(); // get() 직접 호출
        order.setStatus("CANCELED"); // 캡슐화 깨짐, 매직 문자열
    }
}
```

---

## 5. null / Optional 처리 규칙

- 컬렉션 반환은 **`null` 대신 빈 컬렉션**(`List.of()`).
- 단건 조회는 **`Optional`** 로 반환하고 `.get()` 직접 호출을 금지한다(`orElseThrow` 사용).
- 메서드 파라미터의 null 허용 여부를 명확히 하고, 외부 입력은 검증한다.
- `Optional`을 필드/파라미터 타입으로 쓰지 않는다(반환 타입 전용).

```java
// 좋은 예
public Optional<User> findByEmail(String email) { ... }

User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UserNotFoundException(email));

// 나쁜 예
public User findByEmail(String email) {
    return entityManager.find(...); // null 반환 가능 → NPE 유발
}
```

---

## 6. 매직 넘버 / 하드코딩 문자열

- 의미 있는 숫자/문자열은 **상수**로 추출한다.
- 상태/유형은 `enum`으로 표현한다(매직 문자열 금지).

```java
// 좋은 예
public static final int MAX_PAGE_SIZE = 100;
public enum OrderStatus { PENDING, PAID, CANCELED }

// 나쁜 예
if (size > 100) { ... }              // 100의 의미 불명
if (status.equals("CANCELED")) { ... } // 오타/대소문자 위험
```

---

## 7. 예외 처리

- 예외는 **의미 있는 이름과 메시지**를 가진다(`RuntimeException` 그대로 던지지 않기).
- 도메인 예외는 도메인 패키지에 정의하고, 응답 변환은 전역 핸들러에서 한다([error-response-guide](../api/error-response-guide.md)).
- 예외 메시지에 secret/내부 정보를 노출하지 않는다([.claude/rules/security.md](../../.claude/rules/security.md)).

```java
// 좋은 예
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("주문을 찾을 수 없습니다. orderId=" + orderId);
    }
}
```

---

## 8. 주석 / 함수 책임

- 함수는 **하나의 책임**을 가진다. 길어지면 의도 단위로 분리한다.
- "무엇을"이 아니라 "왜"를 주석으로 남긴다(코드로 드러나는 내용은 주석 불필요).
- 공개 API/복잡한 로직에는 JavaDoc을 작성한다([documentation-convention](./documentation-convention.md) §3 참조).

---

## 체크리스트

- [ ] 네이밍 규칙(클래스 PascalCase, 메서드/변수 camelCase, 상수 UPPER_SNAKE)을 지켰는가
- [ ] 패키지 구조와 계층 의존 방향(api→application→domain←infra)을 따랐는가
- [ ] Setter/필드 주입/`@Data`(Entity) 등 지양 항목을 피했는가
- [ ] 단건 조회에 `Optional` + `orElseThrow`를 사용했는가
- [ ] 매직 넘버/하드코딩 문자열을 상수/enum으로 추출했는가
- [ ] 예외에 의미 있는 이름/메시지를 부여하고 secret을 노출하지 않았는가
- [ ] 포맷터/정적 분석(예: spotless/checkstyle)을 통과했는가
