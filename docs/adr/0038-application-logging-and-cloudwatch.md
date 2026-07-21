# ADR-0038. 앱 로깅을 구조화(JSON)+MDC 상관관계로 남기고 CloudWatch Agent로 중앙 수집한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0038 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-07-21 |
| 관련 문서 | [ADR-0001](./0001-bounded-context-module-decomposition.md), [ADR-0002](./0002-inter-module-communication-via-events.md), [ADR-0004](./0004-api-response-envelope.md), [ADR-0010](./0010-jwt-authentication-filter.md), [ADR-0014](./0014-withdrawal-pii-anonymization.md), [ADR-0015](./0015-sensitive-column-encryption.md), [ADR-0019](./0019-infrastructure-as-code-terraform.md), [ADR-0021](./0021-cost-optimization-profile.md), [ADR-0023](./0023-secrets-in-ssm-parameter-store.md), [ADR-0026](./0026-dev-host-memory-budget.md), [ADR-0027](./0027-dev-discord-alerting.md), [ADR-0030](./0030-error-message-i18n-resource-bundle.md), [error-response-guide](../api/error-response-guide.md), [api-design-guide](../api/api-design-guide.md), [issue #152](https://github.com/swyp-app-5th-team1/Kohere-backend/issues/152) |

## Status

Proposed

> 이슈 #152(마일스톤 2.0.0)의 세 요구 — ① BusinessException 외 **모든 예외 로깅**, ② **사용자별 활동 추적**이 가능한 정상 흐름 로깅, ③ 로그를 **CloudWatch로 연동해 한 곳에서 관리** — 를 설계로 확정하는 ADR이다. **구현은 착수 전**이며, 아래 Decision 중 트레이드오프가 갈리는 5개 항목(§Consequences 말미의 "팀 확정 대기")은 착수 전 확정한다.
>
> **핵심 설계 원칙:** 로그의 *내용*(JSON 포맷 + MDC 상관관계)과 *전송 경로*(CloudWatch 수집)는 **직교**한다 — 앱이 구조화 로그를 만들고 인프라가 그 로그를 수집한다. 두 축을 섞지 않는 것이 이 ADR의 뼈대다.

## Context

- **현 상태(코드 확인):** `src/main/resources`에 `logback-spring.xml`이 없고 `application*.yml` 5개 어디에도 `logging.*` 키가 없어 **Spring Boot 기본 콘솔(STDOUT) appender만** 동작한다(파일 appender·JSON 포맷·MDC 패턴 전무). `build.gradle`에 로깅/CloudWatch/logstash 의존성 0건. `@Slf4j`/`log.*` 사용은 main 전체에서 12개 파일·30여 건에 그치고 대부분 인프라 초기화(Mongo 인덱스 이니셜라이저)·이벤트 핸들러의 애드혹 로그다.
- **예외 로깅 실태(코드 확인):** `com.kohere.common.exception.GlobalExceptionHandler`(`@RestControllerAdvice`)는 `handleBusiness`의 **5xx `BusinessException`**(L44)과 `handleUnexpected`(catch-all, L83) **두 곳만** 스택을 남긴다. 4xx `BusinessException`·`MethodArgumentNotValidException`(검증)·`HttpMessageNotReadable`·405·404는 **무로깅**이다. 또한 `handleUnexpected`의 시그니처는 `@ExceptionHandler(Exception.class)`라 `java.lang.Error`(예: 코드베이스의 SOLAPI `NoSuchMethodError` 사례·OOM·StackOverflow)는 Spring이 dispatch 중 `ServletException`으로 감쌀 때만 우연히 잡히고, 감싸지지 않는 경로에서는 새어 나간다.
- **보안 계층 사각지대(코드 확인):** 인증/인가 실패는 `GlobalExceptionHandler`를 **거치지 않는다** — `RestAuthenticationEntryPoint`(401)·`RestAccessDeniedHandler`(403)가 `SecurityErrorResponder`로 응답을 직접 write한다. 따라서 **어떤 401/403도 현재 로그에 남지 않는다.** `@RestControllerAdvice`는 애초에 DispatcherServlet 범위만 커버하므로, 필터에서 던진 예외·`ApplicationRunner`(`MasterAccountSeedRunner`)·Mongo 인덱스 이니셜라이저·향후 `@Async`/`@ApplicationModuleListener` 리스너의 예외도 전부 핸들러 밖이다.
- **요청 진입 훅(코드 확인):** 요청 필터는 `JwtAuthenticationFilter`(`OncePerRequestFilter`) 하나뿐으로, `SecurityConfig.securityFilterChain`에서 `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`로 등록된다. 이 필터가 `Authorization: Bearer`를 `JwtTokenService.parse()`로 검증해 `AuthPrincipal(Long userId, boolean onboarding)`을 SecurityContext에 심는다. **MDC 사용 흔적·HandlerInterceptor·요청 상관관계(traceId)는 전무하다.** `social-login`·`reissue`·actuator·swagger는 Bearer 없는 **익명 요청**이라 principal이 없다.
- **활동 데이터 원천(코드 확인):** `AuthPrincipal`·JWT 클레임에는 **PII가 없다**(userId + 온보딩 스코프만). 이미 도메인 이벤트가 쓰이고 있고(`BookingEventHandler`·`UserWithdrawnEventListener`, 현재 **동기 `@EventListener`**), 두 리스너 모두 TODO에 `@ApplicationModuleListener`(비동기·커밋 후) 전환을 예고한다. `LoggingVerificationSmsSender`에는 이미 `mask(phone)=***+뒤4자리` 마스킹 선례가 있다.
- **배포/인프라 사실(코드 확인):** 실배포는 **dev EC2 단일 박스 + docker-compose**다([ADR-0021](./0021-cost-optimization-profile.md), ECS/ALB/RDS 없음). `Dockerfile`은 `eclipse-temurin:21-jre`에서 `java -jar`로 앱을 띄우고, `app` 서비스(`container_name=kohere-app`, `SPRING_PROFILES_ACTIVE=dev`)로 실행되며 **로그는 전량 STDOUT**으로 나가 `docker logs kohere-app`이 유일한 조회 경로다. docker-compose `app`에는 `logging:` 드라이버 설정도, `mem_limit`도 **없다**(기본 json-file 드라이버). t3.small **2GB**에 mysql·mongo·redis·app(JVM `-Xmx512m`)이 공존하고 스왑 2GB로 완충 중이다([ADR-0026](./0026-dev-host-memory-budget.md)). terraform에 EC2·IAM·SSM 리소스는 있으나 **CloudWatch Logs 관련 리소스·`logs:*` IAM 권한은 전무**하다. 시크릿·환경설정은 SSM Parameter Store→`.env`(refresh-env) 경로로 주입된다([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)·[ADR-0024](./0024-secret-change-propagation.md)).
- **제약:** ⑴ 모듈 경계 — 크로스커팅 로깅 컴포넌트가 도메인 모듈에 얹히면 `ModularityTest`(`ApplicationModules.verify()`)가 깨진다([ADR-0001](./0001-bounded-context-module-decomposition.md)). ⑵ "정상 흐름 결과"인 활동/종료 신호는 에러가 아니므로 공통 에러 래퍼([ADR-0004](./0004-api-response-envelope.md))에 넣지 않는다. ⑶ CloudWatch Logs는 **수집(ingestion, 서울 리전 ~$0.76/GB)이 지배적 비용**이고 저장(retention ~$0.03/GB·월)은 부차적이다 — 비용 통제의 1차 레버는 retention이 아니라 **수집량**이다. ⑷ '모든 예외'는 웹 요청 범위를 넘어 비웹 실행 경로까지 포함해야 성립한다. ⑸ 향후 MSA 전환 시 "한 곳에서 로그 접근" 요구가 커진다.

## Decision

**앱 로그를 구조화(JSON)로 남기고, 요청 단위 상관관계(`traceId`)와 사용자 식별(`userId`)을 MDC로 부착하며, 모든 예외를 웹·비웹 경로에서 빠짐없이 로깅하고, 사용자 활동을 접근 로그(HandlerInterceptor)와 도메인 이벤트 구독으로 남긴다. 이 구조화 로그를 앱이 클린 JSON 파일로 쓰고 CloudWatch Agent가 그 파일을 수집해 한 곳(CloudWatch Logs)에서 관리한다. 모든 크로스커팅 로깅 컴포넌트는 `common`(공유 커널)에 둔다.** 세부는 다음과 같다.

1. **구조화(JSON) 로깅을 도입하되 콘솔과 JSON을 분리한다.** `src/main/resources/logback-spring.xml`을 신설하고 appender를 용도별로 나눈다 — `CONSOLE`(STDOUT, **사람이 읽는 텍스트 패턴**, 전 프로파일)과 `JSON_FILE`(파일 `/logs/app.json`, **JSON 1줄/이벤트**, `<springProfile name="dev,prod">`에서만 활성). JSON 인코더는 `logstash-logback-encoder`(net.logstash.logback) **1개 의존만** 추가한다 — MDC를 최상위 JSON 필드로 자동 승격하며 AWS와 결합하지 않는다(CloudWatch appender가 **아니다**). Logs Insights로 활동을 "데이터화(분석)"하려면 파싱 가능한 JSON이 전제이지만, `docker logs kohere-app`(로컬·SSM 조회)의 가독성을 JSON으로 훼손해선 안 되므로 **콘솔은 텍스트, 파일만 JSON**으로 둔다. 로그 레벨은 `application-{profile}.yml`의 `logging.level.*` 또는 `.env`의 `LOGGING_LEVEL_*`로 토글해 재배포만으로 반영한다.

2. **요청 상관관계는 MDC로 부착하되 MDC를 만지는 필터를 하나로 통일한다.** 최전방에 신규 `MdcLoggingFilter`(`OncePerRequestFilter`, `Ordered.HIGHEST_PRECEDENCE`)를 두어 `traceId`(UUID)를 `put`하고, **이 필터의 `finally`에서 `MDC.clear()`를 단 1회** 수행해 스레드풀 재사용 오염을 원천 차단한다. `userId`는 이미 그 값을 쥔 `JwtAuthenticationFilter`가 인증 성공 분기(principal 확정 지점)에서 직접 `put`한다 — put/clear 생명주기 보장은 최전방 필터의 단일 clear가 흡수하므로 userId 전용 clear는 두지 않는다. 익명 요청(social-login·reissue·actuator·swagger)은 `userId=anonymous`로 표기한다. **별도 필터가 SecurityContext에서 userId를 읽어 넣는 대안은 폐기한다** — MDC를 만지는 필터가 둘로 늘어 책임만 분산된다.

3. **Micrometer Tracing은 지금 도입하지 않는다(자체 `traceId` MDC로 갈음).** 근거 — 단일 EC2 모놀리식이라 서비스 간 전파라는 Tracing의 핵심 이점이 발현되지 않고, 브릿지(brave/otel)+샘플링 설정 오버헤드만 든다. 자체 `traceId`(UUID)만으로 요청 상관관계는 충분하다. **단, MDC 키 이름을 `traceId`로 표준화**해 MSA 전환 시 게이트웨이가 발급한 W3C `traceparent`로 낮은 비용에 이행할 수 있게 둔다(§Decision 9).

4. **모든 예외 로깅은 세 사각지대를 각각 메운다.** (A) **`GlobalExceptionHandler` 보강** — 기존 메서드에 log 한 줄씩 추가한다. 레벨은 `ErrorCode.getHttpStatus()`의 5xx 여부로 자동 분기한다: 4xx(business 4xx·검증·malformed·405)=**WARN(스택 생략)**, 404=**INFO**(봇·스캐너 노이즈), 5xx·uncaught=**ERROR(스택 포함)**. 4xx 로그에는 `error.code`(예: `LISTING_NOT_FOUND`)를 남겨 응답 코드와 상관 분석이 되게 한다. (B) **보안 계층** — `SecurityErrorResponder`(또는 각 EntryPoint/Handler)에 로깅을 추가하되 사유별 레벨을 분리한다: `TOKEN_EXPIRED`=**INFO/DEBUG**(짧은 access + refresh 회전 구조상 만료 401은 일상적 정상 흐름 — WARN이면 이상탐지 신호가 만료 노이즈에 묻히고 수집량만 는다), 위조·서명오류·`FORBIDDEN`·`AUTH_ONBOARDING_REQUIRED`=**WARN**(실제 이상탐지 대상). (C) **비웹 경로** — `handleUnexpected`의 catch 대상을 `Throwable`로 넓혀 `java.lang.Error`를 포착하고, 필터·`ApplicationRunner`·인덱스 이니셜라이저 등 부트스트랩/비웹 실행 지점은 개별 `try-catch`로 ERROR 로깅하며, 스레드풀/비동기 경로에는 공통 `Thread.UncaughtExceptionHandler`를 등록한다. 이 경로들은 MDC가 없으므로 메시지에 실행 컨텍스트(runner명·리스너명)를 명시한다. **`handleUnexpected`를 "최후의 보루"로 부르던 서술은 철회한다** — 그것은 웹 범위 내 `Exception` 한정 안전망일 뿐이다.

5. **활동 로깅은 HandlerInterceptor(접근 로그) + 도메인 이벤트 구독(핵심 비즈니스 이벤트)의 조합으로 하고, AOP는 쓰지 않는다.** 신규 `HandlerInterceptor`가 `preHandle`/`afterCompletion`으로 전 요청의 접근 로그(method·엔드포인트·status·latency)를 남기고, 핵심 유스케이스(진단 완료·예약 생성/삭제·채팅 시작 등)는 도메인 이벤트 구독 로거가 남긴다([ADR-0002](./0002-inter-module-communication-via-events.md)의 이벤트 패턴 재사용). AOP(`@Aspect`)는 `spring-boot-starter-aop` 신규 의존과 프록시 오버헤드 대비 이점이 없어 배제한다. 활동 이벤트는 초기엔 **동기 `@EventListener`**(이벤트 externalization 의존 불필요, 발행 비용이 낮아 요청 스레드 latency 영향 미미)로 두되, **payload에 `traceId`·`actor(userId)`·`target`을 값으로 실어** 발행해 향후 리스너가 비동기(`@ApplicationModuleListener`)로 전환돼도 상관관계가 끊기지 않게 한다 — **활동 로그는 MDC 전파에 의존하지 않는다.** **로그인류(`socialLogin`·`reissue`)는 익명 요청이라 인터셉터/MDC로 actor를 못 잡으므로**, `AuthService`가 인증 결과(발급 유저)의 `userId`로 활동 로그를 직접 발행한다(접근 로그와 조달 경로 분리).

6. **활동 로그 스키마는 코드에서 조달 가능한 필드로만 구성한다.** `timestamp`(UTC — [api-design-guide](../api/api-design-guide.md)) · `traceId` · `actor`(userId 숫자) · `action`(HTTP method+엔드포인트 또는 도메인 이벤트명) · `module`(패키지=BC) · `target`(이벤트 payload id) · `result`(성공 또는 `ErrorCode`) · `latencyMs`. **접근 로그의 기본 커버리지는 '핵심 유스케이스 우선'으로 잡는다** — 성공 요청 전량 INFO는 최대 수집원이라 t3.small 단일 박스에서 비용·부하 리스크가 크다(§Decision 8). health/actuator·정적 리소스는 접근 로그에서 제외한다.

7. **PII는 로그에서 원천 배제하고 마스킹을 공통화한다.** `actor`는 `userId`(숫자)만 쓰므로 활동 로그는 **원천적으로 PII-안전**이다(이 설계의 큰 이점). access/refresh·Apple refresh 토큰·Apple private-key는 **절대 로깅 금지**, 이메일·전화+인증코드·실명·여권·사업자번호는 마스킹한다. `LoggingVerificationSmsSender`의 `mask(phone)` 선례를 `common`의 공통 `LogMasker` 유틸로 승격해 전 모듈이 재사용한다. 요청 바디 전체를 무분별하게 찍지 않는다(검증 실패 로그도 `FieldErrorDetail`의 필드명·사유만, 값은 제외). PII 판단이 애매하면 **로그에 안 남기는 쪽**을 기본값으로 한다([ADR-0014](./0014-withdrawal-pii-anonymization.md)·[ADR-0015](./0015-sensitive-column-encryption.md)의 마스킹 기조 계승).

8. **로그 레벨·보존·비용 정책을 확정한다.** 레벨은 ERROR(5xx·uncaught·`Error`·비웹 스레드 예외, 스택 포함) / WARN(4xx 예외, 인증 위조·서명오류·403, best-effort 실패) / INFO(활동 접근 로그·도메인 이벤트·401 `TOKEN_EXPIRED`·인프라 초기화 — 현재 warn으로 남는 시드/이벤트를 info로 교정) / DEBUG(개발 전용, 서버 프로파일에서 억제)로 둔다. **비용 1차 레버는 수집량**이다: 성공 요청 전량 접근 로그를 기본값에서 제외(핵심 우선), health/actuator·정적 리소스 제외, 404/봇·401 `TOKEN_EXPIRED` INFO 강등, 스택트레이스는 5xx/Error에만. retention은 저장 비용 통제(2차)로 dev Log Group **14~30일** 명시(무기한 금지), prod는 기존 `log_retention_days` 변수와 정합. CloudWatch 비용 리스크(이슈 명시)는 초기 배포 후 실제 **수집량(GB/일)**을 측정해 커버리지·레벨·retention을 조정하는 것으로 관리한다.

9. **CloudWatch 수집은 CloudWatch Agent가 앱이 쓴 클린 JSON 파일을 tail하는 방식으로 한다.** Docker 기본 json-file 드라이버는 각 줄을 `{"log":"…앱JSON…\n","stream":…,"time":…}`로 감싸므로, Agent가 컨테이너 로그(`/var/lib/docker/containers/*/*-json.log`)를 그대로 tail하면 앱 JSON이 `log` 필드 안 escape된 문자열이 되어 Logs Insights가 **이중 파싱**을 해야 한다 → "데이터화" 목표가 깨진다. 그래서 §Decision 1의 `JSON_FILE` appender로 클린 JSON을 마운트 볼륨 파일에 직접 쓰고 **Agent는 그 파일만 tail**한다. 이 구성에서만 원본 JSON이 보존되고 동시에 콘솔 텍스트라 `docker logs` 조회 가치도 유지된다. 이 경로는 SSM 운영 흐름·부트스트랩과 정합하고, 앱 힙과 무관하며(logback appender가 AWS SDK를 물지 않음), 전송 실패가 앱에 영향을 주지 않는다.
   - **IAM은 커스텀 인라인 정책으로 최소권한을 준다** — AWS 관리형 `CloudWatchAgentServerPolicy`는 `logs:*`를 리소스 `*`에 허용하는 광역 정책이라 "최소권한"과 양립하지 않는다. `logs:CreateLogGroup`·`CreateLogStream`·`PutLogEvents`(+`DescribeLogStreams`)를 해당 Log Group ARN에 스코프한 인라인 정책을 EC2 인스턴스 프로파일에 첨부한다(EC2는 IMDSv2+인스턴스 프로파일이라 자격증명 별도 주입 불필요).
   - **Log Group 네이밍은 `/kohere/{env}/{service}` 단일 규약으로 통일**하고 Log Stream은 `{instance-id}/kohere-app`으로 둔다 — MSA 단일 쿼리 목표(§Decision 10)는 단일 규약을 전제로 하므로 prod ECS 선례(`/ecs/<prefix>`)도 이 규약으로 수렴시킬지를 팀이 결정한다.
   - **terraform로 관리**한다: `aws_cloudwatch_log_group`(+retention, `modules/dev/monitoring`) · Agent 설치·config(`modules/dev/host/user_data.sh.tftpl`, Agent config는 SSM Parameter) · 커스텀 IAM 정책(`modules/dev/iam`) · 로그 볼륨 마운트(docker-compose `app`)([ADR-0019](./0019-infrastructure-as-code-terraform.md)).
   - **메모리 실측 게이트를 조건화한다** — CloudWatch Agent의 실제 RSS는 100MB대까지 간다. t3.small 2GB에 mysql·mongo·redis·app이 이미 공존하고 `app`에 `mem_limit`도 없으므로([ADR-0026](./0026-dev-host-memory-budget.md)), Agent 상주 후 여유 메모리를 측정하고 스왑만으로 완충된다는 전제를 재검증하기 **전에는 도입하지 않는다**.

10. **지금 구조화+`traceId`를 잡아 두는 것이 MSA 대비다.** 모든 서비스가 같은 JSON 스키마·단일 Log Group 규약(`/kohere/{env}/{service}`)으로 CloudWatch에 모이면 서비스 무관하게 Logs Insights 단일 쿼리로 추적할 수 있어 이슈의 "한 곳에서 로그 접근" 동기를 그대로 확장한다. MDC 키를 `traceId`로 표준화해 뒀으므로 MSA 도입 시 게이트웨이가 발급한 `traceparent`를 심어 서비스 경계 상관관계로 넓히고, 이때 §Decision 3에서 보류한 Micrometer Tracing을 도입해 전파를 자동화한다. 활동 이벤트가 `traceId`·`actor`를 payload로 운반하므로(§Decision 5) Modulith 이벤트가 외부 메시지 브로커 이벤트로 확장돼도 상관관계가 보존된다.

11. **모든 크로스커팅 로깅 컴포넌트는 `common`(공유 커널)에 둔다.** `MdcLoggingFilter`·활동 접근 인터셉터·`LogMasker`·활동 이벤트 구독 로거·`Thread.UncaughtExceptionHandler` 등록을 도메인 모듈이 아니라 `common`에 두어 `ModularityTest`(모듈 경계 검증)를 통과시킨다([ADR-0001](./0001-bounded-context-module-decomposition.md)). 활동 이벤트 payload는 원시 타입(userId·id 등)만 실어 모듈 간 엔티티 공유를 피한다([ADR-0002](./0002-inter-module-communication-via-events.md)).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| 전 프로파일 콘솔을 JSON으로 통일(파일 분리 없음) | appender 1벌, 설정 단순 | `docker logs kohere-app`(로컬·SSM 조회)이 사람이 읽기 힘든 JSON이 됨 | 콘솔 텍스트 + 파일 JSON 분리로 가독성과 파싱을 동시 확보 |
| 예외 로깅을 요청 필터/AOP에서 일괄 catch | 핸들러 손 안 댐 | `@RestControllerAdvice`의 예외 매핑·응답 계약과 이중화, 4xx/5xx 레벨 구분 로직 중복 | 기존 핸들러에 log 한 줄씩이 최소 침습 |
| `handleUnexpected`를 그대로 두고 `Exception`만 커버 | 변경 최소 | `java.lang.Error`(SOLAPI `NoSuchMethodError`·OOM)가 무로깅으로 누락 — '모든 예외' 미충족 | catch를 `Throwable`로 넓혀 Error까지 포착 |
| 401/403 로깅을 생략(현행 유지) | 작업 없음 | 무차별 토큰 대입·권한 우회 등 **이상탐지 신호가 통째로 사라짐** | 보안 계층이 가장 큰 사각지대 — 사유별 레벨로 로깅 |
| 401 `TOKEN_EXPIRED`를 WARN으로 | 만료를 한눈에 | 짧은 access+refresh 회전상 만료 401은 일상 정상 흐름 — 이상탐지 신호가 노이즈에 묻히고 수집량↑ | INFO/DEBUG로 강등, 위조·서명오류·403만 WARN |
| userId를 별도 필터가 SecurityContext에서 읽어 MDC에 넣기 | traceId·userId 주입 지점 분리 | MDC를 만지는 필터가 둘 → 책임 분산, clear 누락 위험면 확대 | 인증필터가 이미 쥔 지점에서 직접 put, clear는 최전방 1회 |
| Micrometer Tracing(brave/otel) 지금 도입 | traceId 자동 전파, MSA 표준 | 단일 EC2라 서비스 간 전파 이점 없음, 브릿지+샘플링 설정 오버헤드 | 자체 traceId MDC로 충분 — 키명만 표준화, MSA 전환 시 도입 |
| 활동 로깅을 AOP(`@Aspect @Around`)로 | 메서드 인자 접근 | `starter-aop` 신규 의존, 프록시 오버헤드, HTTP 컨텍스트 접근 불편 | 인터셉터(HTTP)+이벤트(도메인) 조합이 의존 0으로 충분 |
| 활동 이벤트를 비동기(`@ApplicationModuleListener`)로 즉시 전환 | 요청 스레드 latency 0 | `spring-modulith-starter-events-jpa/jdbc`(externalization) 신규 의존, MDC 유실 대응 필요 | 동기 `@EventListener` + payload에 traceId·actor로 시작, 비동기는 그때 명시 도입 |
| 로그인 actor를 MDC/인터셉터로 일괄 처리 | 조달 경로 1벌 | social-login·reissue는 익명 요청 → principal 없음 → actor=anonymous(활동추적 핵심이 증발) | 로그인류는 AuthService가 결과 userId로 직접 발행 |
| (B) Docker `awslogs` 드라이버로 CloudWatch 전송 | compose 한 줄, 원본 메시지 보존, prod ECS와 일관 | 컨테이너 로컬 파일이 사라져 `docker logs` **조회 불가** | 로컬·SSM 조회를 잃음 — 클린 JSON 파일 전제에서 Agent 우선 |
| (C) logback CloudWatch appender(AWS SDK) | 앱레벨 제어 최상 | 앱↔AWS 결합, 힙·스레드 부담, 전송 실패가 앱에 영향 | 내용/전송 직교 원칙 위배 — 앱은 파일만, 전송은 인프라 |
| Agent가 컨테이너 json-file을 그대로 tail | 파일 appender 불요 | Docker json-file이 앱 JSON을 `{"log":"…"}`로 **이중 래핑** → Logs Insights 이중 파싱, '데이터화' 붕괴 | 클린 JSON 파일을 별도로 쓰고 그것만 tail |
| IAM 관리형 `CloudWatchAgentServerPolicy` 사용 | 첨부만 하면 됨 | `logs:*`+리소스 `*` 광역 — "최소권한"과 모순 | logs 3~4액션을 Log Group ARN에 스코프한 인라인 정책 |
| dev/prod Log Group 네이밍을 각자 스킴으로 | prod 선례 안 건드림 | 서로 다른 스킴이면 MSA 단일 쿼리 불가 | `/kohere/{env}/{service}` 단일 규약으로 통일 |
| retention을 무기한 또는 짧게(며칠) | 장기 보관 / 저장비 0 | 무기한=저장비 누적, 며칠=사후 조사 불가 | dev 14~30일 명시, 수집량을 1차 비용 레버로 |
| 로깅 유틸을 각 도메인 모듈에 배치 | 모듈 응집 | `ModularityTest` 위반(도메인이 크로스커팅 침범) | 전부 `common` 공유 커널에 |

## Consequences

- **긍정:** 세 완료조건이 각각 검증 가능해진다 — (1) 4xx=WARN·5xx/Error=ERROR·401/403이 사유별 레벨로 남고 비웹 경로까지 커버되어 '모든 예외'가 성립, (2) `userId` 필드로 Logs Insights 필터가 되어 사용자별 활동 추적, (3) CloudWatch Log Group에 이중 래핑 없는 JSON이 적재된다. 로그 내용(JSON+MDC)과 전송(CloudWatch)이 직교라 어느 한쪽을 바꿔도 다른 쪽이 안 흔들린다. `actor=userId`(숫자)라 활동 로그가 원천 PII-안전이고, 구조화+`traceId`를 지금 잡아 두어 MSA 전환이 저비용이다. 크로스커팅 컴포넌트를 `common`에 모아 모듈 경계가 유지된다.
- **부정/트레이드오프:** 신규 의존(`logstash-logback-encoder`)·`logback-spring.xml`·필터·인터셉터·이벤트 구독 로거로 표면적이 는다. `JSON_FILE` 파일 appender는 볼륨 마운트·로테이션 운영을 요구한다. CloudWatch Agent(RSS 100MB대)가 t3.small의 빠듯한 메모리 예산을 잠식할 수 있어 실측 게이트 없이는 OOM 위험이 있다([ADR-0026](./0026-dev-host-memory-budget.md)). 수집량을 방치하면(전량 접근 로그·DEBUG 유출) CloudWatch 비용이 튄다. 활동 이벤트를 비동기로 전환하는 시점에 externalization 의존과 MDC 대응이 추가로 필요하다.
- **후속 작업 / 롤아웃(앱측 먼저, 인프라 나중 — 각 단계 독립 검증):** ① 예외 로깅 보강(핸들러+401/403+비웹·Error, 의존 0) → ② MDC(traceId·userId) → ③ 구조화 로그(`logstash-encoder`+`logback-spring.xml`) → ④ 활동 로깅(인터셉터+이벤트+로그인 직접 발행) → ⑤ CloudWatch(Agent 파일 tail+커스텀 IAM+Log Group+볼륨) → ⑥ 수집량·메모리 실측 후 튜닝. **1~4는 재배포로, 5는 user_data 변경이라 EC2 재생성 1회**가 필요하다 — 재생성 다운타임·`/data/mysql`·`/data/mongo` 볼륨 보존·마스터 계정 재시드 파급을 유지보수 창에서 사전 점검한다([ADR-0025](./0025-dev-db-credential-reconcile.md)). 커밋 전 `./gradlew spotlessApply` 필수.
- **주요 회귀 리스크:** MDC 누수(최전방 필터 `finally` 단일 clear로 차단) · `ModularityTest` 위반(전부 `common`) · OOM(⑤ 실측 게이트) · 비용 폭증(수집량 1차 통제) · PII 유출(`LogMasker`+코드리뷰 게이트) · ⑤ EC2 재생성 파급.
- **팀 확정 대기(착수 전):** ⑴ 전송 경로 — Agent(A, 클린 JSON 파일 tail) vs awslogs(B). ⑵ Micrometer Tracing 도입 시점 — 지금 vs MSA 전환 시(제안: 보류). ⑶ Log Group 네이밍 — prod `/ecs/<prefix>`를 `/kohere/{env}/{service}`로 수렴할지. ⑷ 활동 로그 커버리지 — 핵심 유스케이스(제안 기본값) vs 성공 요청 전량. ⑸ **활동 로그 보존기간 & DB 영속 여부** — 이슈의 "데이터화"를 CloudWatch Logs Insights 쿼리로 충분히 볼지, 아니면 활동 로그를 폴리글랏 DB(Mongo/MySQL)에도 영속할지. 이 결정은 저장소 선택·모듈 소유권까지 파급되므로 착수 전 확정한다(제안: CloudWatch만, DB 영속은 별도 이슈).

## Validation

- **완료조건 직접 확인(이슈 명시):** ⑤ 배포 후 CloudWatch Log Group에 **이중 래핑 없는 JSON**이 적재되는지, 그 JSON을 Logs Insights에서 `userId`·`traceId`·`error.code`로 필터/조회할 수 있는지 직접 확인한다.
- **예외 커버리지:** 로컬/dev에서 4xx(business 4xx·검증·malformed·405)·404·5xx·401(만료 vs 위조)·403·`java.lang.Error`(비웹 경로)를 각각 유발해 기대 레벨(WARN/INFO/ERROR)과 스택 포함 여부가 로그·CloudWatch에 나타나는지 확인한다. `./gradlew build`·`ModularityTest`가 green이어야 한다.
- **상관관계:** 임의 요청이 로그에 `traceId`·`userId`를 달고, 익명 요청이 `anonymous`로 표기되며, 연속 요청 간 MDC 오염(직전 요청의 userId 잔류)이 없는지 확인한다.
- **활동 추적:** 로그인·예약·진단 완료 시 활동 로그가 남고, 로그인류의 `actor`가 `anonymous`가 아니라 발급 유저의 `userId`로 채워지는지 확인한다.
- **비용·메모리 게이트:** ⑤ 이후 실제 수집량(GB/일)과 Agent 상주 후 여유 메모리를 측정해 커버리지·레벨·retention을 조정한다 — 이 실측이 통과하기 전에는 광역 접근 로그·무기한 retention을 켜지 않는다.
- **재검토 시점:** MSA 전환(그때 Micrometer Tracing 도입·Log Group 네이밍 통일·중앙 집계 재검토), 또는 수집량/Agent 메모리가 비용·부하 이슈가 될 때.
