# Code Style Convention (Java · Spring Boot)

> 이 저장소는 **Java / Spring Boot** 백엔드이며, 빌드 도구는 **Gradle**을 사용한다.
> 아키텍처는 **모듈러 모놀리식**(Spring Modulith)으로 가고, 모듈 내부는 **DDD 계층**으로 구성한다.
> 연계 문서: [collaboration-convention](./collaboration-convention.md), [commit-convention](./commit-convention.md), [error-response-guide](../api/error-response-guide.md)
> 기본 포맷 규칙: [.editorconfig](../../.editorconfig) · CI: [.github/workflows/ci.yml](../../.github/workflows/ci.yml)

## 목적

코드 스타일을 한 사람이 쓴 것처럼 통일해 **리뷰 노이즈(포맷 다툼)를 없애고**, 모듈러 모놀리식의
**모듈 경계와 DDD 계층 의존 방향**을 코드 구조로 강제한다. 스타일은 사람이 외우는 규칙이 아니라
**도구(Spotless·Spring Modulith)로 검증**하는 것을 기본으로 한다.

---

## 1. 포맷팅 / 자동화 도구 (Gradle)

베이스 스타일은 **Google Java Style**을 따른다. 이 스타일의 들여쓰기는 **2칸**이라 이미 적용된
[.editorconfig](../../.editorconfig)(`indent_size = 2`)와 일치한다. 포맷은 **Spotless + google-java-format**으로
자동 적용·검증한다.

### 1-1. Gradle 설정 (`build.gradle`)

실제 설정은 [build.gradle](../../build.gradle)을 본다. 핵심 부분:

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '3.5.8'
  id 'io.spring.dependency-management' version '1.1.7'
  id 'com.diffplug.spotless' version '8.6.0'
}

spotless {
  java {
    googleJavaFormat('1.35.0')   // 2칸 들여쓰기 = .editorconfig와 일치
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
```

> google-java-format 1.29.0+는 **JDK 21 이상에서만 실행**되므로, 빌드 JDK는 21로 고정한다
> (`java.toolchain.languageVersion = 21`, CI의 setup-java도 21).

### 1-2. 사용법

| 명령                      | 용도                                   |
| ------------------------- | -------------------------------------- |
| `./gradlew spotlessApply` | 포맷 자동 정렬 (커밋 전 실행)          |
| `./gradlew spotlessCheck` | 포맷 위반 검사 (위반 시 빌드 실패)     |

- 커밋 전 `spotlessApply`로 정렬하는 것을 습관화한다.
- CI([ci.yml](../../.github/workflows/ci.yml))의 `build` job이 PR마다 `./gradlew spotlessCheck build`를 실행한다 — 포맷 위반이나 빌드/테스트 실패 시 머지가 막힌다.

> **결정 메모(들여쓰기):** 한국 Spring 현업에서는 4칸(네이버 핵데이 컨벤션)도 많이 쓴다.
> 4칸으로 바꾸려면 `.editorconfig`의 `indent_size`를 4로 올리고 `googleJavaFormat('1.35.0').aosp()`를 쓴다.
> **현재 합의는 2칸(.editorconfig 유지)** 이며, 바꾸려면 팀 합의 후 두 곳을 함께 변경한다.

---

## 2. 네이밍 규칙

| 대상                 | 규칙             | 예시                              |
| -------------------- | ---------------- | --------------------------------- |
| 클래스/인터페이스    | `UpperCamelCase` | `OrderService`, `OrderRepository` |
| 메서드/변수/필드     | `lowerCamelCase` | `placeOrder`, `orderRepository`   |
| 상수(static final)   | `CONSTANT_CASE`  | `MAX_RETRY_COUNT`                 |
| 패키지               | 전부 소문자      | `com.kohere.order.domain`         |
| 제네릭 타입 파라미터 | 대문자 1글자     | `T`, `E`, `K`, `V`                |

- 의미가 드러나는 이름을 쓴다. `a`, `tmp`, `data` 같은 모호한 이름과 불필요한 약어를 피한다.
- 부정 불리언(`isNotReady`)보다 긍정형(`isReady`)을 쓴다.
- 테스트 메서드는 **무엇을 검증하는지** 드러나게 쓴다(예: `placeOrder_재고가_부족하면_예외를_던진다`).

---

## 3. 아키텍처 — 모듈러 모놀리식 + DDD 계층

### 3-1. 모듈 구조 (Spring Modulith, package-by-module)

**최상위 패키지 = 하나의 모듈(Bounded Context)** 로 본다. 계층(`controller`/`service`...)이 아니라
**도메인(`order`/`user`...)으로 먼저 나눈다.** Spring Modulith는 애플리케이션 루트 바로 아래
top-level 패키지를 모듈로 인식하고, 그 하위 패키지는 기본적으로 모듈 **내부(internal)** 로 취급한다.

```text
com.kohere
├── KohereApplication.java
├── order/                     # 모듈 = Bounded Context
│   ├── package-info.java      # @ApplicationModule (경계·허용 의존 선언)
│   ├── presentation/          # 표현 계층
│   ├── application/           # 응용 계층
│   ├── domain/                # 도메인 계층 (모듈의 핵심)
│   └── infrastructure/        # 인프라 계층
├── user/
│   └── ... (동일 구조)
└── common/                    # 공유 커널 (예외·공통 타입 등, OPEN 모듈)
```

### 3-2. 모듈 경계 규칙

- **모듈 내부 타입은 package-private**(접근 제어자 없음)으로 두어 다른 모듈에서 못 쓰게 한다.
  다른 모듈에 노출할 타입만 `public`으로 둔다.
- **모듈 간 직접 호출 대신 이벤트(Application Events)로 통신**해 결합을 낮춘다.
  비동기 처리는 `@ApplicationModuleListener`(= `@Async` + `@Transactional` + 트랜잭션 이벤트)를 쓴다.
- **JPA 엔티티를 모듈 간 공유하지 않는다.** 같은 데이터가 필요하면 각자 엔티티/뷰를 두거나 이벤트로 받는다(영속 계층 결합은 가장 풀기 어렵다).
- `package-info.java`에 `@ApplicationModule(allowedDependencies = …)`로 **허용 의존을 화이트리스트**로 선언해, 의도치 않은 의존과 순환 의존을 빌드 시점에 막는다.
- 경계는 **`@ApplicationModuleTest` / `ApplicationModules.verify()`** 로 테스트에서 지속 검증한다.

### 3-3. DDD 계층 (모듈 내부)

각 모듈 안은 DDD 4계층으로 구성한다. **의존은 항상 도메인을 향한다**(안쪽이 바깥을 모른다).

```text
presentation ──▶ application ──▶ domain ◀── infrastructure
```

- **표현(Presentation) — `presentation`**
  - 책임: REST 컨트롤러, 요청/응답 DTO, 입력 형식 검증
  - 두지 않을 것: 비즈니스 로직
- **응용(Application) — `application`**
  - 책임: 유스케이스 조율, 트랜잭션 경계, 도메인 호출·이벤트 발행
  - 두지 않을 것: 도메인 규칙 자체
- **도메인(Domain) — `domain`** *(모듈의 핵심)*
  - 책임: Aggregate·Entity·Value Object, 도메인 서비스, **Repository 인터페이스**, 도메인 이벤트
  - 두지 않을 것: 프레임워크/DB 의존
- **인프라(Infrastructure) — `infrastructure`**
  - 책임: **Repository 구현(JPA)**, 외부 연동 어댑터, 설정
  - 두지 않을 것: 비즈니스 규칙

- **Repository는 인터페이스를 `domain`에, 구현을 `infrastructure`에** 둔다(의존성 역전). 도메인은 영속 기술을 모른다.
- 컨트롤러는 **엔티티를 그대로 노출하지 않고** 요청/응답 DTO로 변환한다.
- 도메인 규칙(불변식·상태 전이)은 **엔티티/도메인 서비스 안**에 둔다. 응용 계층은 흐름만 조율한다.

### 3-4. 의존성 주입

- **생성자 주입(constructor injection)만 사용**한다. `final` 필드로 두어 불변성과 테스트 용이성을 확보한다.
- **`@Autowired` 필드 주입은 쓰지 않는다**(테스트 어렵고 순환 의존을 숨긴다).
- 보일러플레이트는 Lombok `@RequiredArgsConstructor`로 제거한다. `@AllArgsConstructor`는 불필요한 필드까지 주입되므로 쓰지 않는다.

```java
@Service
@RequiredArgsConstructor
public class PlaceOrderService {
  private final OrderRepository orderRepository;   // 인터페이스(domain)에 의존
  // ...
}
```

---

## 4. null / 매직 넘버 / 상태 표현

- 반환값에 null 대신 **`Optional`** 을 쓴다. 컬렉션은 null 대신 **빈 컬렉션**을 반환한다.
- 외부 입력은 경계(컨트롤러·응용 계층)에서 검증하고, 도메인 안에서는 **유효한 값만** 다룬다(`@NotNull` 등 Bean Validation 활용).
- **매직 넘버·매직 스트링은 상수**로 뽑는다. 의미가 있으면 도메인 의미를 담은 상수명을 쓴다.
- 정해진 상태/분류는 `String` 대신 **enum**으로 표현한다(예: `OrderStatus.PAID`).

---

## 5. 예외 처리

- 도메인/비즈니스 예외는 의미가 드러나는 이름의 커스텀 예외로 던진다(예: `OutOfStockException`). `~Exception`으로 끝낸다.
- 컨트롤러에서 `try/catch`로 응답을 만들지 않고, **`@RestControllerAdvice` 전역 핸들러**에서 일관된 에러 응답으로 변환한다.
- 응답 스키마·에러 코드는 [error-response-guide](../api/error-response-guide.md)를 따른다.
- 예외를 삼키지 않는다(`catch (Exception e) {}` 금지). 복구 불가능하면 전파하고, 로그에 맥락을 남긴다.

---

## 6. 주석 / 함수 책임

- 주석은 **"왜"** 를 설명한다. "무엇을" 하는지는 이름과 코드로 드러낸다(commit/PR 컨벤션과 같은 철학).
- 코드를 따라 읽으면 아는 내용을 중복 서술하지 않는다. 죽은 주석·주석 처리된 코드는 지운다.
- 공개 API·도메인 규칙처럼 의도가 비자명한 곳에는 Javadoc으로 계약을 남긴다.
- **함수는 한 가지 일만** 한다. 한 메서드가 여러 책임을 지면 분리한다. 깊은 중첩은 **early return**으로 푼다.

---

## 체크리스트

- [ ] 커밋 전 `./gradlew spotlessApply`로 포맷을 정렬했다
- [ ] 네이밍 규칙(클래스 UpperCamelCase / 메서드·필드 lowerCamelCase / 상수 CONSTANT_CASE)을 지켰다
- [ ] 새 코드는 도메인 모듈(`com.kohere.<module>`) 아래에 두었고, 모듈 내부 타입은 package-private이다
- [ ] 모듈 간에는 직접 호출/엔티티 공유 대신 공개 API·이벤트로 통신했다
- [ ] DDD 계층 의존이 도메인을 향한다(Repository 인터페이스는 domain, 구현은 infrastructure)
- [ ] 의존성은 생성자 주입(`@RequiredArgsConstructor`)으로 받았고 필드 주입을 쓰지 않았다
- [ ] null 대신 Optional/빈 컬렉션, 매직 넘버 대신 상수/enum을 사용했다
- [ ] 예외는 전역 핸들러(`@RestControllerAdvice`)로 처리하고 삼키지 않았다

---

## 참고 자료

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [google/google-java-format](https://github.com/google/google-java-format)
- [Spotless (diffplug/spotless)](https://github.com/diffplug/spotless)
- [Spring Modulith — Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Modular Monolith Structure in Spring Boot Backends](https://medium.com/@AlexanderObregon/modular-monolith-structure-in-spring-boot-backends-24c10c9b8b07)
- [Spring Boot Best Practices — JavaGuides](https://www.javaguides.net/2019/03/spring-boot-best-practices.html)
- [Service Layer Pattern in Java With Spring Boot — foojay.io](https://foojay.io/today/service-layer-pattern-in-java-with-spring-boot/)
