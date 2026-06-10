# Security Policy

> 예시(Spring Boot 3.x / Java 17 / Spring Security(JWT) / PostgreSQL 기준)입니다.
> 코드/설정 예시는 실제 프로젝트의 스택과 정책에 맞게 교체하세요.
> 권한 모델은 [access-control.md](./access-control.md)를 참고합니다. (시크릿 관리는 본 문서 §시크릿 비커밋 원칙)

## 목적

이 문서는 백엔드 서비스의 **보안 기본 원칙**과 그것을 코드/운영에 적용하는 **구체적 기준**을 정의합니다.
"무엇을 지켜야 하는가"뿐 아니라 "어떻게 지키는가(예시)"를 함께 제공해, 신규 도메인/팀에서 그대로
재사용할 수 있도록 합니다.

적용 범위

- 애플리케이션 코드(API 계층 ~ 데이터 접근 계층)
- 인증/인가, 입력 검증, 출력 인코딩
- 의존성 및 빌드 산출물
- 로그/관측(Observability)에서의 민감정보 취급
- 시크릿(비밀값) 관리의 코드 측면 (§시크릿 비커밋 원칙)

---

## 핵심 보안 원칙

| 원칙 | 정의 | 적용 예시 |
| --- | --- | --- |
| 최소 권한(Least Privilege) | 주체에게 작업에 필요한 최소 권한만 부여 | DB 계정을 앱용/마이그레이션용으로 분리, RDS는 `SELECT/INSERT/UPDATE/DELETE`만 |
| 심층 방어(Defense in Depth) | 한 계층이 뚫려도 다음 계층이 막도록 다중 방어 | WAF + 인증 필터 + 메서드 보안 + DB row 권한 |
| 안전한 기본값(Secure by Default) | 명시적으로 허용하지 않으면 거부 | Spring Security `anyRequest().authenticated()`, CORS 화이트리스트 |
| 실패 시 차단(Fail Securely) | 예외/장애 시 권한을 열지 말고 닫음 | 토큰 검증 예외 → 401, 권한 평가 실패 → 403 |
| 최소 노출(Minimize Attack Surface) | 불필요한 엔드포인트/포트/기능 비활성화 | Actuator는 `health,info`만 노출, 디버그 엔드포인트 제거 |
| 신뢰 경계 명시(Trust Boundary) | 외부 입력은 신뢰하지 않고 경계에서 검증 | Controller에서 검증, 도메인에서 불변식 재확인 |
| 감사 가능성(Auditability) | 보안 관련 행위는 추적 가능하게 기록 | 로그인/권한변경/관리자 액션 감사 로그 |

---

## 인증(Authentication)

> 예시: JWT(Access + Refresh) 기반 stateless 인증.

원칙

- 인증은 **신뢰 경계 진입점(API Gateway / Security Filter)** 에서 1회 수행하고, 결과를 컨텍스트로 전달한다.
- 비밀번호는 **단방향 해시**로 저장한다. 평문/가역 암호화 금지.
- 토큰은 **짧은 수명 Access + 회전(rotation) Refresh** 구조를 권장한다.

| 항목 | 예시 기준 | 비고 |
| --- | --- | --- |
| 비밀번호 해시 | BCrypt (cost 10~12) 또는 Argon2id | `PasswordEncoder` 빈으로 통일 |
| Access Token 수명 | 15분 (`900s`) | 짧게 유지 |
| Refresh Token 수명 | 14일, 1회용 회전 | 재사용 감지 시 전체 무효화 |
| 서명 알고리즘 | RS256(비대칭) 권장 / HS256(대칭) 가능 | 키는 시크릿 매니저 |
| 토큰 전달 | `Authorization: Bearer <token>` | URL 쿼리스트링 금지 |
| 클레임 | `sub, iss, aud, exp, iat, roles, scope` | 상세는 [access-control.md](./access-control.md) |

비밀번호 해시 빈 예시

```java
@Bean
PasswordEncoder passwordEncoder() {
    // 운영에서는 cost를 부하 테스트로 결정 (예: 12)
    return new BCryptPasswordEncoder(12);
}
```

체크리스트

- [ ] 평문 비밀번호를 로그/응답/DB에 절대 노출하지 않는다.
- [ ] 토큰 서명 키는 코드/리포지토리에 없고 시크릿 매니저에서 주입된다.
- [ ] 토큰 만료/위변조/알고리즘 혼동(`alg=none`) 검증이 있다.
- [ ] 로그아웃/비밀번호 변경 시 Refresh 무효화 경로가 있다.

---

## 인가(Authorization)

> 권한 모델(RBAC), 역할/권한 매트릭스, JWT 스코프, 검사 위치의 상세 예시는
> [access-control.md](./access-control.md)에 있습니다. 여기서는 원칙만 요약합니다.

- 인가는 **인증 이후** 수행하며, "인증됨 == 허용됨"이 아니다(별개의 단계).
- **거부 기본값**: 명시적으로 허용한 리소스/액션 외에는 거부.
- 객체 수준 권한(소유자 검사)을 빠뜨리지 않는다. (예: 다른 사용자의 주문 조회 차단 = IDOR 방지)
- 권한 판단은 **클라이언트 입력이 아니라 서버 측 신뢰 출처**(토큰 클레임/DB)로 한다.

객체 소유권 검사 예시

```java
@PreAuthorize("hasRole('USER')")
public OrderDto getMyOrder(Long orderId, Long currentUserId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND"));
    if (!order.getOwnerId().equals(currentUserId)) {
        // 존재 여부 노출을 피하려면 404를 반환하기도 한다.
        throw new ForbiddenException("ORDER_FORBIDDEN");
    }
    return OrderDto.from(order);
}
```

---

## 입력 검증(Input Validation)

원칙: **모든 외부 입력은 신뢰하지 않는다.** 경계(Controller/DTO)에서 검증하고, 도메인에서 불변식을 재확인한다.

| 대상 | 위협 | 방어 예시 |
| --- | --- | --- |
| SQL 쿼리 | SQL Injection | 파라미터 바인딩(JPA/PreparedStatement), 문자열 연결 금지 |
| 경로/파일명 | Path Traversal | 화이트리스트, `..` 제거, 정규화 후 검증 |
| 정수/길이/범위 | 비즈니스 우회 | `@Min/@Max/@Size`, 서버측 한도 |
| 외부 URL | SSRF | 도메인 화이트리스트, 사설 IP 대역 차단 |
| 직렬화 입력 | 역직렬화 공격 | 신뢰 타입만 허용, 다형 역직렬화 비활성 |

DTO 검증 예시 (Bean Validation)

```java
public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 10, max = 72) String password,
    @Size(max = 50) String nickname
) {}

@PostMapping("/api/v1/users")
public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
    // @Valid 실패 시 400 + 표준 에러 응답 (error-response-guide 참고)
    return ResponseEntity.status(201).body(userService.create(req));
}
```

> 에러 응답 형식은 [error-response-guide](../api/error-response-guide.md)와 일관되게 유지합니다.

---

## 출력 인코딩 / 응답 보안(Output Encoding)

- 응답은 **컨텍스트에 맞게 인코딩**한다. JSON API는 `Content-Type: application/json`을 고정하고,
  사용자 입력을 그대로 HTML/스크립트로 반환하지 않는다.
- 에러 응답에 **스택트레이스/내부 경로/SQL/버전 정보를 노출하지 않는다.**
- 보안 헤더를 기본 적용한다.

권장 보안 응답 헤더 예시

| 헤더 | 예시 값 | 목적 |
| --- | --- | --- |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | HTTPS 강제 |
| `X-Content-Type-Options` | `nosniff` | MIME 스니핑 방지 |
| `X-Frame-Options` | `DENY` | 클릭재킹 방지 |
| `Content-Security-Policy` | `default-src 'none'` (API 기준) | 리소스 출처 제한 |
| `Cache-Control` | `no-store` (민감 응답) | 민감 데이터 캐시 방지 |

```java
http.headers(h -> h
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentTypeOptions(Customizer.withDefaults())
    .frameOptions(FrameOptionsConfig::deny)
);
```

---

## 의존성 취약점 관리(Dependency Management)

원칙: 알려진 취약점(CVE)이 있는 의존성을 운영에 배포하지 않는다.

| 단계 | 도구 예시 | 동작 |
| --- | --- | --- |
| 빌드 시 스캔 | OWASP Dependency-Check, `gradle dependencyCheckAnalyze` | High/Critical 발견 시 빌드 실패 |
| SCA/자동 PR | Dependabot, Renovate | 취약 버전 업그레이드 PR 자동 생성 |
| 컨테이너 스캔 | Trivy, Grype | 베이스 이미지 취약점 검사 |
| 라이선스/SBOM | CycloneDX | 공급망 추적 |

운영 기준 예시

- **Critical/High**: 발견 후 영업일 기준 빠른 패치(예: 7일 이내), 핫픽스 검토.
- **Medium 이하**: 정기 업그레이드 사이클에 포함.
- 패치 불가 시 **완화책(미사용 경로/네트워크 격리)** 을 ADR로 기록. (예: [docs/adr](../adr/))

체크리스트

- [ ] CI에 의존성 취약점 스캔 단계가 있다.
- [ ] Critical/High는 머지 차단(또는 라벨 후 추적)된다.
- [ ] 베이스 이미지 태그를 고정하고 정기 갱신한다.

---

## 로깅 시 민감정보 마스킹(Sensitive Data in Logs)

원칙: **민감정보는 로그/추적/메트릭에 남기지 않는다.** 부득이하면 마스킹한다.

마스킹 대상 예시

| 분류 | 예시 | 로그 표현 |
| --- | --- | --- |
| 인증정보 | 비밀번호, 토큰, 쿠키 | `****` (전체 마스킹) |
| 개인정보(PII) | 이메일, 전화번호, 주민/여권번호 | `u***@example.com`, `010-****-1234` |
| 결제정보 | 카드번호, CVV | `**** **** **** 1234`, CVV 미기록 |
| 비밀 헤더 | `Authorization`, `Cookie`, `Set-Cookie` | 로그 필터에서 제거 |

마스킹 유틸 예시

```java
public final class MaskingUtil {
    private MaskingUtil() {}

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String head = name.isEmpty() ? "" : name.substring(0, 1);
        return head + "***" + domain; // u***@example.com
    }
}
```

로그 예시 (Good vs Bad)

```text
# Bad  - 토큰/비밀번호 평문 노출
INFO  login success user=test@example.com password=p@ssw0rd token=eyJhbGciOi...

# Good - 식별만 가능, 비밀값은 없음
INFO  login success userId=10293 email=t***@example.com  // token/password 미기록
```

> 관측 표준(로그 필드/레벨/추적 ID)은 [observability](../architecture/observability.md)를 따릅니다.

체크리스트

- [ ] 요청/응답 바디 전체 로깅을 기본 비활성화한다.
- [ ] `Authorization`/`Cookie` 등 비밀 헤더를 로그 마스킹 필터로 제거한다.
- [ ] 예외 로그에 비밀번호/토큰/카드번호가 포함되지 않는다.

---

## 시크릿 비커밋 원칙(No Secrets in Repo)

원칙: **비밀값은 저장소에 절대 커밋하지 않는다.** 절차/도구는 아래 핵심 요약을 따른다.

핵심 요약

- 비밀값은 **환경변수 / 시크릿 매니저(예: AWS Secrets Manager, Vault)** 로 주입한다.
- `.env`, `*.pem`, `*-credentials.json` 등은 `.gitignore`로 차단한다.
- 커밋 전 **시크릿 스캐너(gitleaks, truffleHog)** 로 검사한다.
- 노출 사고 발생 시: **즉시 키 회전 → 영향 범위 조사 → 히스토리 정리 → 사후 기록**.

```text
# .gitignore 예시 (발췌)
.env
.env.*
*.pem
*.key
**/credentials*.json
```

> Claude Code는 `.env`, private key, credentials 파일을 읽거나 출력하지 않습니다(CLAUDE.md Safety Rules).

---

## 보안 사고 대응 연계

보안 사고(취약점/유출/침해)는 운영 인시던트 프로세스와 연계합니다.

- 대응 절차/심각도 분류: [incident-response](../operations/incident-response.md)
- 시크릿 유출 시 키 회전: 본 문서 §시크릿 비커밋 원칙

---

## 전체 보안 점검 체크리스트

- [ ] 모든 보호 엔드포인트는 인증 후 접근 가능하다(거부 기본값).
- [ ] 객체 소유권/권한 검사가 누락된 엔드포인트가 없다.
- [ ] 입력 검증이 경계에서 수행되고 SQL/경로/URL 주입을 방어한다.
- [ ] 에러 응답에 내부 정보(스택트레이스/SQL/버전)가 없다.
- [ ] 보안 응답 헤더가 적용된다.
- [ ] 의존성 취약점 스캔이 CI에 있고 Critical/High를 차단한다.
- [ ] 로그/추적에 비밀값·PII가 없거나 마스킹된다.
- [ ] 시크릿이 저장소에 없고 스캐너가 CI에 있다.
- [ ] 프로젝트 확정 후 실제 스택 값으로 본 문서를 갱신했다.
