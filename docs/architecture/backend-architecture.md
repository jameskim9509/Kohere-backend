# Backend Architecture

> **예시(Spring Boot 기준) 문서입니다.** 패키지 구조/코드/규칙은 **예시 스택(Spring Boot 3.x / Java 17 / Gradle / Spring Data JPA / PostgreSQL / Flyway / Spring Security(JWT))** 기준입니다. 실제 프로젝트 스택이 확정되면 패키지명·어노테이션·규칙을 교체하세요. 스택 미정 상태 규칙은 [CLAUDE.md](../../CLAUDE.md)를 따릅니다.

## 목적

계층(Layer) 단위의 책임 분리, **의존 방향 규칙**, **트랜잭션 경계**를 정의한다. 이 문서를 따르면 도메인 로직이 프레임워크/DB/외부 시스템에 오염되지 않고, 테스트 가능하며, 변경 영향이 국소화된다.

- 레이어드 아키텍처와 헥사고날(포트&어댑터) 아키텍처의 관계를 설명한다.
- 표준 Java 패키지 구조 예시를 제공한다.
- 계층 간 의존 방향 규칙과 위반 시 처리를 명시한다.
- `@Transactional` 트랜잭션 경계 원칙을 정의한다.

---

## 1. 레이어드 vs 헥사고날

두 모델은 대립이 아니라 **같은 원칙(의존성 역전)의 다른 표현**이다. 핵심은 "도메인은 바깥(프레임워크/DB/외부)을 모른다"는 것.

```text
  레이어드(전통적)                헥사고날(포트 & 어댑터)

  ┌──────────────┐              인바운드 어댑터        아웃바운드 어댑터
  │  API 계층     │            (Controller)          (JPA Repo, HTTP)
  ├──────────────┤                 │                       ▲
  │ Application   │                 ▼                       │
  ├──────────────┤            ┌─────────┐            ┌─────────────┐
  │  Domain      │            │ InPort  │            │   OutPort   │
  ├──────────────┤            │(UseCase)│──────┐  ┌─►│ (Interface) │
  │ Infrastructure│           └────┬────┘     │  │  └─────────────┘
  └──────────────┘                 ▼          │  │
   (위→아래 의존)            ┌───────────────────────┐
                            │   Domain / Application │
                            │   (외부를 모름, 순수)   │
                            └───────────────────────┘
```

- **레이어드**: 위 계층이 아래 계층에 의존. 단순하고 익숙하다. 소규모/CRUD 중심 서비스에 적합.
- **헥사고날**: 도메인이 인터페이스(Port)만 알고, 구현(Adapter)은 바깥에 둔다. 외부 의존이 많거나 도메인 복잡도가 높을 때 유리.
- **이 저장소 기본 권장**: 레이어드를 기본으로 하되, **외부 연동·영속성은 인터페이스(Port)로 추상화**하여 헥사고날의 장점을 취한다. 즉 `domain`은 `infra`를 컴파일타임에 모른다.

---

## 2. 패키지 구조 예시 (Java / Spring Boot)

도메인별로 묶는 **패키지-by-feature**를 기본으로 하고, 그 안에서 계층을 나눈다. 아래는 `order` 도메인 예시다.

```text
com.example.app
├── ApplicationMain.java
├── common/                         # 공통(횡단) - 특정 도메인에 속하지 않음
│   ├── config/                     # SecurityConfig, JpaConfig 등
│   ├── error/                      # 공통 예외, @RestControllerAdvice
│   ├── support/                    # ApiResponse, PageResponse 등 래퍼
│   └── annotation/
└── order/                          # ── 도메인: 주문 ──
    ├── api/                        # [API 계층]
    │   ├── OrderController.java     #   @RestController
    │   └── dto/                     #   OrderCreateRequest, OrderResponse
    ├── application/                # [애플리케이션 계층]
    │   ├── OrderService.java        #   유스케이스, @Transactional
    │   └── port/                    #   아웃바운드 포트(인터페이스)
    │       ├── OrderRepository.java     #   영속성 포트
    │       └── PaymentGateway.java      #   외부연동 포트
    ├── domain/                     # [도메인 계층] - 프레임워크 의존 최소
    │   ├── Order.java               #   Entity / Aggregate Root
    │   ├── OrderStatus.java         #   Value Object / enum
    │   └── OrderPolicy.java         #   도메인 규칙
    └── infra/                      # [인프라 계층] - 포트의 구현(어댑터)
        ├── persistence/
        │   └── OrderJpaRepository.java  # OrderRepository 구현(JPA)
        └── client/
            └── TossPaymentGateway.java  # PaymentGateway 구현(HTTP)
```

| 계층 | 패키지 | 책임 | 의존해도 되는 곳 | 대표 어노테이션 |
| --- | --- | --- | --- | --- |
| API | `api` | 요청/응답 변환, 검증, 인증 진입 | `application` | `@RestController`, `@Valid` |
| Application | `application` | 유스케이스, 트랜잭션, 포트 정의 | `domain`, `application.port` | `@Service`, `@Transactional` |
| Domain | `domain` | 비즈니스 규칙, 불변식 | (없음 / 순수) | (가급적 무) |
| Infra | `infra` | 포트 구현(DB·외부) | `application.port`, `domain` | `@Repository`, `@Component` |

---

## 3. 의존 방향 규칙

의존은 **항상 안쪽(도메인)을 향한다**. 바깥(인프라)으로 향하는 컴파일타임 의존은 금지한다.

```text
  api ───────► application ───────► domain ◄─────── infra
                   │                                  ▲
                   └──────► application.port ◄────────┘
                            (인터페이스)     (구현/주입)
```

**허용 / 금지 매트릭스** (행이 열을 import 해도 되는가):

| from \ to | api | application | domain | infra |
| --- | --- | --- | --- | --- |
| **api** | — | 허용 | (DTO만) | 금지 |
| **application** | 금지 | — | 허용 | 금지(포트로만) |
| **domain** | 금지 | 금지 | — | **금지** |
| **infra** | 금지 | port만 | 허용 | — |

- 핵심 금지: **`domain`은 `infra`/`api`/`application`을 import 하지 않는다.**
- 외부/DB 호출은 `application`이 정의한 **포트 인터페이스**를 통해서만 한다. 구현은 `infra`가 제공하고 DI로 주입한다(의존성 역전).
- 위반 자동 차단: ArchUnit 테스트로 강제한다(예시).

```java
@AnalyzeClasses(packages = "com.example.app")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infra =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..infra..", "..api..");

    @ArchTest
    static final ArchRule layered = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Api").definedBy("..api..")
        .layer("Application").definedBy("..application..")
        .layer("Domain").definedBy("..domain..")
        .layer("Infra").definedBy("..infra..")
        .whereLayer("Api").mayNotBeAccessedByAnyLayer()
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infra");
}
```

> 모듈(도메인) 단위의 더 큰 경계와 금지 의존은 [module-boundary](module-boundary.md)에서 다룬다.

---

## 4. 트랜잭션 경계

트랜잭션은 **애플리케이션 계층(Service)의 유스케이스 메서드**를 경계로 한다. Controller나 Repository에는 트랜잭션을 두지 않으며, **하나의 유스케이스 = 하나의 트랜잭션**을 기본으로 한다.

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional   // 쓰기 유스케이스 = 하나의 트랜잭션 경계 (Controller/Repository에는 두지 않음)
    public OrderResult placeOrder(PlaceOrderCommand cmd) {
        Order order = Order.create(cmd.userId(), cmd.items());
        orderRepository.save(order);
        return OrderResult.from(order);
    }
}
```

읽기 전용(`readOnly`)·전파(propagation)·격리수준·락 전략·외부 호출 분리(커밋 후 처리/Outbox)·롤백 규칙 등 **트랜잭션 상세 정책은 [transaction-policy](../database/transaction-policy.md)를 정본으로 따른다.** 외부 연동 회복탄력성(타임아웃/재시도/서킷브레이커)은 [external-integration](external-integration.md), 예외→응답 변환은 [error-response-guide](../api/error-response-guide.md)을 참고한다.

---

## 관련 문서

- 시스템 전체 구성: [system-overview](system-overview.md)
- 모듈 경계: [module-boundary](module-boundary.md)
- 외부 연동: [external-integration](external-integration.md)
- 예외 처리: [error-response-guide](../api/error-response-guide.md)
- 트랜잭션 정책(DB): [transaction-policy](../database/transaction-policy.md)
- 코드 스타일: [code-style](../convention/code-style.md)

---

## 체크리스트

- [ ] 확정 스택의 실제 패키지/모듈 네이밍으로 §2 교체
- [ ] ArchUnit(또는 동등 도구) 의존 규칙 테스트 추가 및 CI 연결
- [ ] 트랜잭션 안에서 외부 호출하는 코드가 없는지 점검
- [ ] 읽기 유스케이스에 `readOnly = true` 적용 여부 점검
