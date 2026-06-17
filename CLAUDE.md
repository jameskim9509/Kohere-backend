# CLAUDE.md

이 저장소에서 작업하는 Claude Code를 위한 안내다. 상세는 [`docs/`](docs/index.md)를 정본으로 참조한다.

## 프로젝트

**Kohere** — 외국인 맞춤 주거 유형 탐색 서비스(모바일 앱)의 백엔드. 5단계 진단으로 매물을 추천하고 임대인과 인앱 채팅으로 연결한다. 개요·범위는 [docs/project/project-brief.md](docs/project/project-brief.md).

## 스택

- **Java 21**, **Spring Boot 3.5**, Spring MVC, **Spring Modulith**(모듈러 모놀리식), **Gradle**. (Boot 4.1→3.5 다운그레이드 경위는 [ADR-0016](docs/adr/0016-downgrade-to-spring-boot-3.md))
- Lombok, Bean Validation. 포맷은 **Spotless + google-java-format(2-space)**.
- 영속(JPA/DB)·보안(Security) 스택은 도입 진행 중 — 정확한 의존성은 [build.gradle](build.gradle) 참조. 인증 토큰 전략은 [ADR-0003](docs/adr/0003-jwt-auth-after-oauth-login.md).
- **영속은 폴리글랏**: 문서·지오성(매물 `listing`+`favorite`+`recent-listing`, 진단 `diagnosis`)은 **MongoDB**, 관계·트랜잭션성(`auth`·`user`·`community`)은 **MySQL**. cross-store 조인·트랜잭션 금지(애플리케이션 레벨 조인). 결정: [ADR-0005](docs/adr/0005-polyglot-persistence.md). 단, **refresh 토큰 저장은 Redis**(회전·TTL): [ADR-0006](docs/adr/0006-refresh-token-store-redis.md).

## 빌드 · 테스트

| 목적 | 명령 |
| --- | --- |
| 포맷 정렬(커밋 전 필수) | `./gradlew spotlessApply` |
| 포맷 검사 | `./gradlew spotlessCheck` |
| 빌드 + 테스트 | `./gradlew build` |
| 테스트만 | `./gradlew test` |
| 모듈 경계 검증 | `ModularityTest`(`ApplicationModules.verify()`) |

CI([.github/workflows/ci.yml](.github/workflows/ci.yml))가 PR마다 `spotlessCheck build`를 돌린다 — 포맷 위반/빌드·테스트 실패 시 머지가 막힌다.

## 아키텍처 (필독: [docs/convention/code-style.md](docs/convention/code-style.md))

- **패키지 = 모듈(Bounded Context)**: `com.kohere.<module>` — `auth` · `user` · `listing` · `diagnosis` · `booking` · `chat` · `community` · `gamification` · `report` · `common`(공유 커널). 결정: [ADR-0001](docs/adr/0001-bounded-context-module-decomposition.md).
- 각 모듈 내부는 **DDD 4계층**: `presentation → application → domain ← infrastructure`. 의존은 항상 도메인을 향한다. Repository 인터페이스는 `domain`, 구현은 `infrastructure`.
- **모듈 간 직접 호출·엔티티 공유 금지** → **도메인 이벤트(Application Events)** 로 통신(예: `booking` → `chat`의 `BookingCreatedEvent`). 결정: [ADR-0002](docs/adr/0002-inter-module-communication-via-events.md).
- 모듈 내부 타입은 package-private, 노출할 것만 `public`. `package-info.java`의 `@ApplicationModule`로 허용 의존을 화이트리스트로 선언.
- **생성자 주입만**(`@RequiredArgsConstructor`), 필드 주입 금지. null 대신 `Optional`/빈 컬렉션, 상태는 enum.

## API 규약 ([docs/api/](docs/api/api-design-guide.md))

- 모든 엔드포인트는 **`/api/v1`** 프리픽스, 응답은 **공통 래퍼 `{ success, data, error }`**(`common/response/ApiResponse`). 결정: [ADR-0004](docs/adr/0004-api-response-envelope.md).
- **인증**: 소셜 로그인(Apple/Google) → 서버 JWT. **access=JWT(stateless)**, **refresh=불투명(opaque) 토큰(서버 해시 저장)**. 헤더 `Authorization: Bearer <accessToken>`.
- **에러**: `common/exception`의 `ErrorCode` enum + `GlobalExceptionHandler`(`@RestControllerAdvice`). 코드 카탈로그·status 매핑은 [error-response-guide](docs/api/error-response-guide.md).
- 설계 규약(페이지네이션·필터·지도 검색·날짜 UTC·enum UPPER_SNAKE·금액 KRW 정수)은 [api-design-guide](docs/api/api-design-guide.md).

## 어디에 무엇이 있나 ([docs/index.md](docs/index.md) = 전체 목차)

| 찾는 것 | 위치 |
| --- | --- |
| 도메인별 엔드포인트 상세 스펙 | [docs/api/specs/](docs/api/specs/README.md) (`01-auth-onboarding` … `07-reports`) |
| 유저 스토리 + 인수 조건(AC) | [docs/requirements/user-stories.md](docs/requirements/user-stories.md) |
| 시퀀스 다이어그램(사용자→앱→보안 필터→모듈→저장소) | [docs/architecture/sequence-diagrams/](docs/architecture/sequence-diagrams/README.md) |
| 아키텍처 결정 기록 | [docs/adr/](docs/adr/README.md) |
| 구현 코드 | `src/main/java/com/kohere/<module>/{presentation,application,domain,infrastructure}` |

스펙(`docs/api/specs/`)·유저 스토리·시퀀스 다이어그램·코드(`src/.../<module>/`)는 **도메인 단위로 1:1 대응**한다. 한 도메인을 바꿀 땐 네 곳을 함께 본다.

## 협업 규약 (커밋·PR)

- **Fork 기반 + 이슈 우선**: GitHub Issue 생성 → `<type>/<issue#>-<설명>` 브랜치(개인 fork) → PR `origin:feature → upstream:develop` → **Squash merge**. [collaboration-convention](docs/convention/collaboration-convention.md), [branch-convention](docs/convention/branch-convention.md).
- **커밋**: Conventional Commits, **한국어 subject** — `<type>(<scope>): <subject>`(50자 이내, 마침표 없음). 이슈는 PR 본문 `Closes #N`, 커밋 footer `Refs: #N`. [commit-convention](docs/convention/commit-convention.md).
- 커밋 전 `./gradlew spotlessApply`로 포맷을 정렬한다.
