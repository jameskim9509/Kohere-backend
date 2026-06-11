# Module Boundary

> **예시(Spring Boot 기준) 문서입니다.** 아래 모듈명/패키지/의존 규칙은 **예시 스택(Spring Boot 3.x / Java 17 / Gradle)** 기준 예시입니다. 실제 도메인이 확정되면 모듈 목록과 경계를 교체하세요. 스택 미정 규칙은 [CLAUDE.md](../../CLAUDE.md)를 따릅니다.

## 목적

도메인(기능) **모듈 단위의 경계**를 정의한다. 계층(layer) 경계가 "세로"라면, 모듈 경계는 "가로"다. 모듈마다 공개 인터페이스를 명확히 하고 **의존 방향을 단방향**으로 유지하여, 순환 의존과 의도치 않은 결합을 막는다.

- 모듈 목록과 각 모듈의 책임/공개 인터페이스/의존을 표로 정의한다.
- 모듈 간 의존 방향 다이어그램을 제공한다.
- 금지된 의존(예: `domain`이 `infra`를 모름, 모듈 간 내부 접근 금지)을 명시한다.

> 계층 단위 의존 규칙은 [backend-architecture](backend-architecture.md) §3을 함께 본다. 이 문서는 그 위에서 **모듈 간** 규칙을 다룬다.

---

## 1. 모듈 표 (예시)

| 모듈 | 책임 | 공개 인터페이스(공개 패키지) | 의존하는 모듈 | 비고 |
| --- | --- | --- | --- | --- |
| `common` | 공통 응답/예외/유틸/설정 | `common.support`, `common.error` | (없음) | 누구나 의존 가능. 도메인 지식 금지 |
| `user` | 사용자/계정 | `user.api`, `user.application.UserQuery` | `common` | 다른 모듈에 사용자 조회 제공 |
| `auth` | 인증·토큰 발급/검증 | `auth.api`, `auth.application.TokenService` | `common`, `user` | JWT 발급/검증 |
| `order` | 주문 유스케이스 | `order.api`, `order.application.OrderFacade` | `common`, `user`, `payment` | 주문 생성/조회 |
| `payment` | 결제 처리·외부 PG 연동 | `payment.application.PaymentPort` | `common` | PG 어댑터 격리 |
| `notification` | 알림(메일/SMS/푸시) | `notification.application.Notifier` | `common` | 이벤트 구독, 비동기 |

**공개/비공개 규칙**

- 각 모듈은 **공개 패키지(api, application의 일부 인터페이스)** 만 외부에 노출한다.
- `domain`, `infra`, `application`의 구현체는 **모듈 내부(package-private)** 로 취급한다. 다른 모듈이 import 하지 않는다.
- 모듈 간 호출은 공개 인터페이스(예: `UserQuery`, `PaymentPort`, `Notifier`) 를 통해서만 한다.

---

## 2. 의존 방향 다이어그램

의존은 **상위 유스케이스 → 하위 지원 모듈 → common** 방향의 단방향(DAG)이어야 한다. 화살표 반대 방향, 사이클은 금지.

```text
                    ┌─────────────┐
                    │   order     │  (유스케이스 조합)
                    └──┬───┬───┬──┘
            ┌──────────┘   │   └───────────┐
            ▼              ▼               ▼
      ┌──────────┐   ┌──────────┐   ┌──────────────┐
      │  user    │   │ payment  │   │ notification │
      └────┬─────┘   └────┬─────┘   └──────┬───────┘
           │              │                │
   ┌───────┘              │                │
   ▼                      ▼                ▼
┌──────┐            ┌──────────────────────────┐
│ auth │───────────►│          common          │
└──────┘            └──────────────────────────┘
        (auth는 user에 의존, 모두 common에 의존)
```

- `common`은 잎(leaf) 노드. 어떤 도메인 모듈에도 의존하지 않는다.
- `order`는 조합(facade) 모듈로 여러 하위 모듈을 사용하지만, 하위 모듈은 `order`를 모른다.
- 모듈 간 통신이 양방향으로 필요하면 **이벤트(발행/구독)** 로 디커플링한다 (예: `order` → `notification`은 직접 호출 대신 `OrderPlacedEvent` 발행).

---

## 3. 금지된 의존 (Anti-patterns)

| # | 금지 사항 | 이유 | 대안 |
| --- | --- | --- | --- |
| 1 | `domain` → `infra`/`api` import | 도메인이 프레임워크/DB에 오염됨 | 포트 인터페이스 + 의존성 역전 |
| 2 | 모듈 A가 모듈 B의 `infra`/`domain` 내부 클래스 import | 캡슐화 파괴, 변경 전파 | B의 **공개 인터페이스**만 사용 |
| 3 | 모듈 간 순환 의존(A↔B) | 빌드/이해/테스트 곤란 | 이벤트로 분리, 공통 모듈 추출 |
| 4 | `common`이 특정 도메인을 import | leaf여야 함 | 도메인 지식은 해당 모듈로 이동 |
| 5 | `api` 계층을 다른 계층/모듈이 import | 진입점은 의존 대상이 아님 | 유스케이스(application) 호출 |
| 6 | 엔티티(`domain`)를 모듈 경계 밖으로 그대로 노출 | 내부 모델 누수 | DTO/조회 인터페이스로 변환 후 노출 |

**자동 검증 예시 (ArchUnit)**

```java
@ArchTest
static final ArchRule no_module_cycles =
    slices().matching("com.example.app.(*)..")
        .should().beFreeOfCycles();

@ArchTest
static final ArchRule modules_only_use_public_api =
    noClasses().that().resideOutsideOfPackage("..payment..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..payment.infra..", "..payment.domain..");
    // 외부에서는 payment의 공개 인터페이스(application.PaymentPort)만 사용
```

> Gradle 멀티모듈로 분리할 경우, 위 규칙은 모듈 간 `implementation` 의존 그래프로도 강제할 수 있다. 모듈 분리 결정은 ADR로 남긴다 ([adr/README](../adr/README.md)).

---

## 관련 문서

- 계층 구조와 의존 규칙: [backend-architecture](backend-architecture.md)
- 시스템 전체 구성: [system-overview](system-overview.md)
- ADR 작성: [adr/README](../adr/README.md)

---

## 체크리스트

- [ ] 확정 도메인으로 §1 모듈 표 교체(책임/공개 인터페이스/의존)
- [ ] 모듈 의존 그래프가 DAG(순환 없음)인지 ArchUnit으로 검증
- [ ] 엔티티가 모듈 경계 밖으로 노출되지 않는지 점검
- [ ] 멀티모듈 분리 여부를 ADR로 기록
