# System Overview

> **예시(Spring Boot 기준) 문서입니다.** 이 문서의 다이어그램/표/스택 값은 **예시 스택(Spring Boot 3.x / Java 17 / Gradle / Spring Data JPA / PostgreSQL / Flyway / JUnit5 + Mockito + Testcontainers / Spring Security(JWT))** 기준으로 작성되었습니다. 실제 프로젝트 스택이 확정되면 각 값을 교체하세요. 기술 스택 확정 전 규칙은 [CLAUDE.md](../../CLAUDE.md)와 [.claude/rules/tech-stack.md](../../.claude/rules/tech-stack.md)를 따릅니다.

## 목적

이 문서는 시스템의 **큰 그림**을 한 장으로 제공한다. 처음 합류한 개발자가 "무엇이, 무엇과, 어떻게 연결되는가"를 빠르게 파악하고, 각 컴포넌트의 책임과 비기능 목표를 확인하도록 돕는다.

- 시스템 컨텍스트(누가/무엇이 우리 시스템을 호출하고, 우리는 무엇을 호출하는가)를 정의한다.
- 주요 컴포넌트와 책임을 표로 정리한다.
- 예시 기술 스택과 비기능 요구사항(NFR) 요약을 제공한다.
- 더 자세한 내용으로 향하는 진입점(레이어 구조, 모듈 경계, 연동 패턴 등) 역할을 한다.

---

## 1. 시스템 컨텍스트 다이어그램

아래 다이어그램은 **C4 모델의 Level 1(System Context)** 에 해당한다. 박스 안의 이름과 포트는 예시이며 실제 값으로 교체한다.

```text
                          ┌───────────────────────────┐
                          │        클라이언트          │
                          │  (Web SPA / Mobile App /   │
                          │   3rd-party API Consumer)  │
                          └─────────────┬─────────────┘
                                        │ HTTPS (REST/JSON)
                                        │ Authorization: Bearer <JWT>
                                        ▼
                 ┌──────────────────────────────────────────┐
                 │              API Gateway / LB             │
                 │   (Nginx / ALB · TLS 종료 · 라우팅)        │
                 └─────────────────────┬────────────────────┘
                                       │
                                       ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │                  Backend Application (Spring Boot)                 │
   │                                                                    │
   │   ┌────────────┐   ┌──────────────┐   ┌─────────────┐             │
   │   │ Controller │ → │  Application  │ → │   Domain    │             │
   │   │  (API 계층) │   │  (Service)   │   │ (비즈니스)   │             │
   │   └────────────┘   └──────┬───────┘   └─────────────┘             │
   │                           │                                       │
   │                  ┌────────┴─────────┐                             │
   │                  ▼                  ▼                             │
   │          ┌──────────────┐   ┌──────────────┐                     │
   │          │  Repository  │   │   Gateway    │                     │
   │          │ (JPA/영속성)  │   │ (외부연동 격리)│                    │
   │          └──────┬───────┘   └──────┬───────┘                     │
   └─────────────────┼──────────────────┼─────────────────────────────┘
                     │                  │
        ┌────────────┘                  └──────────────┬───────────────┐
        ▼                                              ▼               ▼
┌───────────────┐   ┌───────────────┐   ┌─────────────────────┐ ┌──────────────┐
│  PostgreSQL   │   │  Redis (캐시/  │   │  외부 시스템(예시)   │ │ 관측 백엔드   │
│  (주 데이터)   │   │   세션/락)     │   │  · 결제 PG          │ │ (Prometheus/ │
│               │   │               │   │  · 메일/SMS 발송     │ │  Grafana/    │
│  Flyway 관리   │   │               │   │  · OAuth Provider   │ │  Loki/Tempo) │
└───────────────┘   └───────────────┘   └─────────────────────┘ └──────────────┘
```

- **인바운드 경계**: 클라이언트 → Gateway → Backend. 인증은 JWT 검증으로 Backend 진입 시점에 수행한다.
- **아웃바운드 경계**: 외부 시스템 호출은 모두 **Gateway/Adapter** 로 격리한다. 자세한 패턴은 [external-integration](external-integration.md) 참고.
- **데이터 경계**: 영속 데이터는 PostgreSQL, 휘발성/캐시/분산락은 Redis. 스키마 변경은 Flyway로만 적용한다.

---

## 2. 주요 컴포넌트 표

| 컴포넌트 | 책임 | 입력 | 출력 | 예시 기술 |
| --- | --- | --- | --- | --- |
| API Gateway / LB | TLS 종료, 라우팅, 레이트 리밋 | HTTPS 요청 | 백엔드로 포워딩 | Nginx / AWS ALB |
| Controller (API 계층) | 요청 검증, DTO 매핑, 인증/인가 진입점 | HTTP 요청 | HTTP 응답(JSON) | Spring MVC `@RestController` |
| Application (Service) | 유스케이스 오케스트레이션, 트랜잭션 경계 | DTO/Command | DTO/Result | `@Service`, `@Transactional` |
| Domain | 핵심 비즈니스 규칙·불변식 | 도메인 객체 | 도메인 객체 | POJO Entity / Value Object |
| Repository | 영속성 접근, 쿼리 | 도메인 객체/식별자 | 도메인 객체 | Spring Data JPA |
| Gateway/Adapter | 외부 시스템 호출 격리, 회복탄력성 | 도메인 요청 | 도메인 응답 | `RestClient` / Feign |
| PostgreSQL | 주 데이터 저장 | SQL | 행(rows) | PostgreSQL 15+ |
| Redis | 캐시·세션·분산락·레이트 리밋 | 명령 | 값 | Redis 7+ |
| 관측 백엔드 | 로그/메트릭/추적 수집·시각화 | 텔레메트리 | 대시보드/알림 | Prometheus·Grafana·Loki·Tempo |

> 계층 간 의존 방향과 패키지 구조는 [backend-architecture](backend-architecture.md), 모듈 단위 경계는 [module-boundary](module-boundary.md)에서 다룬다.

---

## 3. 기술 스택 (예시)

> 아래 표는 **예시**다. 확정되면 [.claude/rules/tech-stack.md](../../.claude/rules/tech-stack.md)와 이 표를 동시에 갱신한다.

| 구분 | 선택(예시) | 비고 |
| --- | --- | --- |
| Language | Java 17 (LTS) | record, sealed, pattern matching 활용 |
| Framework | Spring Boot 3.x | Jakarta EE 9+, Spring 6 기반 |
| Build | Gradle (Kotlin DSL) | `./gradlew build` |
| Persistence | Spring Data JPA / Hibernate | 영속성 계층 격리 |
| Database | PostgreSQL 15+ | 주 데이터 저장소 |
| Migration | Flyway | `V{버전}__{설명}.sql` |
| Cache / Lock | Redis 7+ | 캐시, 세션, 분산락 |
| Auth | Spring Security + JWT | 무상태 인증 |
| Testing | JUnit5 + Mockito + Testcontainers | 단위/통합/E2E |
| Observability | Micrometer + Prometheus + Grafana + Loki + Tempo | RED/USE 메트릭, 분산추적 |
| Deployment | Docker 이미지 + (K8s/ECS 등) | 환경별 설정 외부화 |

---

## 4. 비기능 요구사항(NFR) 요약

상세 정의와 측정 기준은 [non-functional-requirements](../requirements/non-functional-requirements.md)를 정본으로 한다. 아래는 아키텍처 결정의 기준이 되는 요약이다(값은 예시).

| 속성 | 목표(예시) | 아키텍처 반영 |
| --- | --- | --- |
| 가용성 | 월 99.9% | 무상태 서버 + 다중 인스턴스, 헬스체크 |
| 응답시간 | p95 < 300ms (읽기 API) | 캐시, 인덱스, N+1 제거 |
| 처리량 | 피크 500 RPS | 수평 확장, 커넥션 풀 튜닝 |
| 데이터 정합성 | 금전 거래는 강한 정합성 | 트랜잭션 경계 명확화, idempotency |
| 보안 | 전송 구간 TLS, 저장 시 민감정보 암호화 | JWT 검증, 시크릿 외부화 |
| 회복탄력성 | 외부 장애 시 graceful degradation | 타임아웃·재시도·서킷브레이커 |
| 관측성 | 장애 탐지 5분 이내 | 구조화 로그·traceId·알림 |

---

## 5. 관련 문서

- 계층 구조와 패키지: [backend-architecture](backend-architecture.md)
- 모듈 경계와 금지 의존: [module-boundary](module-boundary.md)
- 외부 연동 패턴: [external-integration](external-integration.md)
- 예외/실패 처리: [error-response-guide](../api/error-response-guide.md)
- 로그/메트릭/추적/운영 지표: [observability](observability.md)
- 비기능 요구사항 정본: [non-functional-requirements](../requirements/non-functional-requirements.md)

---

## 체크리스트

- [ ] 실제 클라이언트/외부 시스템 목록으로 컨텍스트 다이어그램 교체
- [ ] 확정 스택으로 §3 기술 스택 표 갱신 ([tech-stack.md](../../.claude/rules/tech-stack.md) 동기화)
- [ ] §4 NFR 목표값을 [non-functional-requirements](../requirements/non-functional-requirements.md)와 일치시키기
- [ ] 새 외부 연동/데이터 저장소 추가 시 다이어그램·컴포넌트 표 갱신
