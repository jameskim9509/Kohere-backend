# Testing Strategy

> 예시(Spring Boot 기준) 문서입니다.
> 기준 스택: **Spring Boot 3.x / Java 17 / Gradle / Spring Data JPA / PostgreSQL / Flyway / JUnit5 + Mockito + Testcontainers / Spring Security(JWT)**
> 이 스택은 어디까지나 예시이며, 실제 프로젝트가 확정되면 비율/목표/도구 값을 프로젝트에 맞게 교체하세요.

## 목적

테스트 전략은 "무엇을, 어디서, 어느 수준까지 검증할지"에 대한 팀의 합의입니다.
이 문서는 테스트 피라미드 기준 비율, 커버리지 목표, 계층별 검증 책임, CI 연동 기준을 정의합니다.

관련 문서:

- 단위 테스트: [unit-test-guide](./unit-test-guide.md)
- 통합 테스트: [integration-test-guide](./integration-test-guide.md)
- E2E 테스트: [e2e-test-guide](./e2e-test-guide.md)
- 테스트 데이터: [test-data-guide](./test-data-guide.md)
- 아키텍처 계층: [backend-architecture](../architecture/backend-architecture.md)
- 에러 처리: [error-response-guide](../api/error-response-guide.md)

---

## 1. 테스트 피라미드

```
            /\
           /  \        E2E (5~10%)
          /----\       - 핵심 사용자 시나리오만
         /      \      - 느림, 비싸다, 깨지기 쉽다
        /--------\     Integration (20~30%)
       /          \    - DB/메시지/외부 어댑터 경계 검증
      /------------\   Unit (60~70%)
     /              \  - 도메인/비즈니스 로직, 빠르고 많이
    /----------------\
```

| 계층 | 비율(예시) | 무엇을 검증하나 | 대표 도구 | 속도 |
| --- | --- | --- | --- | --- |
| 단위(Unit) | 60~70% | 도메인 규칙, 서비스 로직 분기, 경계값/예외 | JUnit5, Mockito | 매우 빠름(ms) |
| 통합(Integration) | 20~30% | JPA 매핑, 쿼리, 트랜잭션, 어댑터 | @SpringBootTest, @DataJpaTest, @WebMvcTest, Testcontainers | 보통(초) |
| E2E | 5~10% | 사용자 시나리오 전체 흐름(HTTP→DB) | RestAssured, Testcontainers, (별도 환경 시 k6/Playwright) | 느림(수초~분) |

> 비율은 "테스트 개수"의 대략적 목표 분포입니다. 숫자에 집착하기보다, **빠른 피드백을 주는 단위 테스트를 두텁게** 가져가는 원칙을 따르세요.

### 안티패턴: 아이스크림 콘(Ice Cream Cone)

E2E가 비대하고 단위가 빈약하면 CI가 느려지고, 실패 원인 파악이 어려워집니다. 피해야 할 형태입니다.

```
   \----------------/   E2E 과다 (느림 / 불안정 / 디버깅 난해)
    \--------------/    Integration
     \----------/       Unit 빈약  ← 안티패턴
      \--------/
```

---

## 2. 계층별 검증 책임 매트릭스

아키텍처 계층([backend-architecture](../architecture/backend-architecture.md))과 테스트 계층을 매핑합니다.

| 아키텍처 계층 | 주 검증 테스트 | 검증 대상 | mock/실제 |
| --- | --- | --- | --- |
| 도메인(Domain) | 단위 | 엔티티 불변식, 값 객체, 도메인 서비스 | 의존성 없음(POJO) |
| 애플리케이션(Service) | 단위 | 유스케이스 분기, 트랜잭션 흐름, 예외 변환 | Repository/Gateway는 mock |
| API(Controller) | 슬라이스 통합 | 요청 검증, 직렬화, 상태코드, 인증 | @WebMvcTest + MockMvc |
| 인프라(Repository) | 슬라이스 통합 | JPA 매핑, JPQL/QueryDSL, 제약조건 | @DataJpaTest + Testcontainers |
| 외부 연동(Adapter) | 통합 | HTTP/메시지 직렬화, 타임아웃, 재시도 | WireMock / Testcontainers |
| 전체 흐름 | E2E | 회원가입→로그인→비즈니스 시나리오 | 실제 앱 + 실제 DB(Testcontainers) |

---

## 3. 커버리지 목표 (예시)

커버리지는 "최소 안전망"이며 목표 그 자체가 아닙니다. 의미 있는 분기/예외를 덮는지가 핵심입니다.

| 지표 | 목표(예시) | 측정 도구 | CI 게이트 |
| --- | --- | --- | --- |
| Line Coverage(전체) | ≥ 70% | JaCoCo | warn |
| Line Coverage(도메인·서비스) | ≥ 80% | JaCoCo (패키지 룰) | **fail** |
| Branch Coverage(도메인·서비스) | ≥ 70% | JaCoCo | **fail** |
| 신규/변경 코드(diff) | ≥ 80% | JaCoCo + diff 플러그인 | **fail** |

> DTO, 설정 클래스, 단순 getter/setter, 생성된 코드(QueryDSL Q타입)는 커버리지 집계에서 제외하세요.

### JaCoCo 게이트 예시 (`build.gradle`)

```groovy
jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = 'PACKAGE'
            includes = ['com.example.app.domain.*', 'com.example.app.application.*']
            limit {
                counter = 'BRANCH'
                value   = 'COVEREDRATIO'
                minimum = 0.70
            }
        }
    }
    // QueryDSL Q타입, DTO, config 제외
    afterEvaluate {
        classDirectories.setFrom(
            files(classDirectories.files.collect {
                fileTree(dir: it, exclude: [
                    '**/Q*.class', '**/*Dto.class', '**/*Config.class',
                    '**/*Application.class'
                ])
            })
        )
    }
}
check.dependsOn jacocoTestCoverageVerification
```

---

## 4. 테스트 분리와 실행 전략

빠른 단위 테스트와 느린 통합/E2E 테스트를 분리해 로컬 피드백을 빠르게 유지합니다.

| 그룹 | 태그(JUnit5 @Tag) | 로컬 기본 실행 | CI 단계 |
| --- | --- | --- | --- |
| 단위 | `unit` | 항상 | 모든 PR |
| 통합 | `integration` | 선택(`./gradlew integrationTest`) | 모든 PR |
| E2E | `e2e` | 거의 안 함 | main 머지/야간 |

### Gradle 태스크 분리 예시

```groovy
test {
    useJUnitPlatform { excludeTags 'integration', 'e2e' }
}

tasks.register('integrationTest', Test) {
    useJUnitPlatform { includeTags 'integration' }
    shouldRunAfter test
}

tasks.register('e2eTest', Test) {
    useJUnitPlatform { includeTags 'e2e' }
    shouldRunAfter integrationTest
}
```

```java
@Tag("integration")
@SpringBootTest
class MeetingRepositoryIntegrationTest { /* ... */ }
```

---

## 5. CI 연동

### 파이프라인 단계(예시)

```
PR open ──> [lint/format] ──> [unit] ──> [integration] ──> [coverage gate] ──> ✅
                                                                │
main merge ──────────────────────────────────────────────────┴──> [e2e (야간/머지)]
```

### GitHub Actions 예시 (`.github/workflows/test.yml`)

```yaml
name: test
on: [pull_request]
jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'
      # Testcontainers는 러너의 Docker 데몬을 사용
      - name: Unit + Integration
        run: ./gradlew test integrationTest jacocoTestReport jacocoTestCoverageVerification
      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: build/reports/jacoco/test/html
```

> CI 러너에서 Testcontainers를 쓰려면 Docker 데몬이 필요합니다. 환경변수/시크릿은 [secrets-management](../security/secrets-management.md) 기준을 따르고, 절대 테스트 코드에 하드코딩하지 마세요.

---

## 6. 무엇을 테스트하고, 무엇을 하지 않는가

| 테스트한다 | 테스트하지 않는다 |
| --- | --- |
| 도메인 규칙/불변식 (예: 모임 정원 초과 거부) | 프레임워크 자체 동작 (JPA가 save를 하는지) |
| 서비스 분기/예외 변환 | 단순 위임만 하는 패스스루 메서드 |
| 경계값/실패 케이스 | getter/setter, toString |
| 직렬화/역직렬화 계약(API contract) | 외부 라이브러리 내부 구현 |
| 트랜잭션 롤백 동작 | 로그 메시지 문자열 그 자체 |

---

## 7. 안정성(Flaky 방지) 원칙

- 시간 의존 로직은 `Clock`을 주입해 고정값으로 테스트한다.
- 랜덤 값은 고정 seed 또는 명시적 입력으로 대체한다 ([test-data-guide](./test-data-guide.md) 참고).
- 테스트 간 데이터는 격리한다(트랜잭션 롤백 또는 컨테이너 재사용+정리).
- `Thread.sleep` 대신 Awaitility 등 폴링 기반 대기를 쓴다.
- 테스트 순서에 의존하지 않는다(`@TestMethodOrder` 남용 금지).

---

## 체크리스트

- [ ] 테스트 피라미드 비율 목표를 팀과 합의했다
- [ ] 커버리지 목표와 CI 게이트(fail 조건)를 정했다
- [ ] 단위/통합/E2E를 태그로 분리하고 Gradle 태스크를 구성했다
- [ ] CI에서 Docker(Testcontainers) 사용 가능 여부를 확인했다
- [ ] 제외 대상(DTO/Q타입/config) 커버리지 룰을 설정했다
- [ ] Flaky 방지 원칙(Clock/seed/격리)을 가이드에 반영했다
- [ ] 프로젝트 확정 후 실제 스택 값으로 본 문서를 갱신했다
