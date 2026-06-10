# Access Control

> 예시(Spring Boot 3.x / Spring Security(JWT) / RBAC 기준)입니다.
> 역할 이름, 권한 코드, 클레임 구조는 실제 도메인에 맞게 교체하세요.
> 상위 보안 원칙은 [security-policy.md](./security-policy.md), 시크릿 취급은
> [secrets-management.md](./secrets-management.md)를 참고합니다.

## 목적

이 문서는 **누가(주체) 무엇을(리소스) 어떻게(액션) 할 수 있는가**를 정의하는 접근 제어(인가) 모델을
제공합니다. RBAC(Role-Based Access Control)를 기본으로 하며, 역할/권한 매트릭스, JWT 클레임·스코프
구조, Spring Security에서의 권한 검사 위치를 구체 예시로 보여줍니다.

용어

| 용어 | 정의 | 예시 |
| --- | --- | --- |
| 주체(Principal) | 인증된 사용자/서비스 | `userId=10293` |
| 역할(Role) | 권한의 묶음 | `ROLE_USER`, `ROLE_ADMIN` |
| 권한(Permission/Authority) | 개별 액션 수행 자격 | `order:read`, `user:delete` |
| 스코프(Scope) | 토큰에 부여된 액세스 범위 | `orders.read profile.write` |
| 리소스(Resource) | 보호 대상 객체 | `Order`, `User`, `Payment` |

---

## RBAC 역할 정의

> 예시 역할입니다. 역할 수는 적게 유지하고(역할 폭발 방지), 세분화는 권한(Permission)으로 표현하길 권장합니다.

| 역할 | 설명 | 대표 권한 묶음 |
| --- | --- | --- |
| `ROLE_GUEST` | 비인증/공개 영역만 접근 | 공개 조회 |
| `ROLE_USER` | 일반 사용자 | 본인 리소스 CRUD |
| `ROLE_SUPPORT` | 고객지원(읽기 위주) | 사용자 조회, 주문 조회 |
| `ROLE_ADMIN` | 운영 관리자 | 사용자/주문 관리, 권한 부여 |
| `ROLE_SYSTEM` | 서비스 간 호출(M2M) | 배치/내부 API |

역할 계층 예시 (상위 역할이 하위를 포함)

```text
ROLE_ADMIN
  └─ includes → ROLE_SUPPORT
                  └─ includes → ROLE_USER
                                  └─ includes → ROLE_GUEST
```

```java
@Bean
RoleHierarchy roleHierarchy() {
    RoleHierarchyImpl h = new RoleHierarchyImpl();
    h.setHierarchy("""
        ROLE_ADMIN > ROLE_SUPPORT
        ROLE_SUPPORT > ROLE_USER
        ROLE_USER > ROLE_GUEST
        """);
    return h;
}
```

---

## 역할 × 권한 매트릭스

> `C=Create, R=Read, U=Update, D=Delete`, `Own=본인 리소스만`, `-=불가`

| 권한(Permission) | GUEST | USER | SUPPORT | ADMIN |
| --- | :---: | :---: | :---: | :---: |
| `user:read` | - | R(Own) | R | R |
| `user:update` | - | U(Own) | - | U |
| `user:delete` | - | - | - | D |
| `order:create` | - | C | - | C |
| `order:read` | - | R(Own) | R | R |
| `order:update` | - | U(Own) | - | U |
| `order:cancel` | - | U(Own) | U | U |
| `payment:read` | - | R(Own) | R(masked) | R |
| `admin:role-assign` | - | - | - | CUD |
| `public:catalog:read` | R | R | R | R |

> `R(masked)`: 카드번호 등은 마스킹된 형태로만 노출 (마스킹 기준은 [security-policy.md](./security-policy.md) 참고).

---

## 리소스별 권한(엔드포인트 매핑)

| 메서드 + 경로 | 필요 역할/권한 | 추가 검사 |
| --- | --- | --- |
| `GET /api/v1/catalog/**` | 공개(permitAll) | 없음 |
| `POST /api/v1/users` | 공개(회원가입) | 입력 검증 |
| `GET /api/v1/users/{id}` | `ROLE_USER` | 본인(`id==sub`) 또는 `ROLE_SUPPORT+` |
| `PATCH /api/v1/users/{id}` | `ROLE_USER` | 본인만(또는 ADMIN) |
| `DELETE /api/v1/users/{id}` | `ROLE_ADMIN` | 감사 로그 |
| `POST /api/v1/orders` | `ROLE_USER` | 없음 |
| `GET /api/v1/orders/{id}` | `ROLE_USER` | 소유자 검사 |
| `POST /api/v1/orders/{id}/cancel` | `ROLE_USER` | 소유자 또는 `ROLE_SUPPORT+` |
| `POST /api/v1/admin/roles` | `ROLE_ADMIN` | 감사 로그, 자기 권한 상향 금지 |
| `GET /actuator/health` | 공개 | 상세 정보 제한 |
| `GET /actuator/**`(그 외) | `ROLE_ADMIN` | 노출 최소화 |

---

## JWT 클레임 / 스코프 예시

> 비밀값/실제 키는 포함하지 않습니다. 아래는 **디코딩된 페이로드 예시**입니다. 서명 키는 시크릿 매니저로 관리합니다.

```json
{
  "iss": "https://auth.example.com",
  "aud": "backend-api",
  "sub": "10293",
  "iat": 1717920000,
  "exp": 1717920900,
  "jti": "f1c2e3a4-0000-4abc-9def-0123456789ab",
  "roles": ["ROLE_USER"],
  "scope": "orders.read orders.write profile.read",
  "tenant": "example-tenant"
}
```

클레임 설명

| 클레임 | 의미 | 검증 포인트 |
| --- | --- | --- |
| `iss` | 발급자 | 신뢰 발급자 화이트리스트와 일치 |
| `aud` | 대상 서비스 | 본 API 식별자와 일치 |
| `sub` | 주체(사용자 ID) | 객체 소유권 검사에 사용 |
| `exp` / `iat` | 만료 / 발급시각 | 만료 검증, 시계 오차(clock skew) 허용 |
| `jti` | 토큰 ID | 블랙리스트/재사용 탐지 |
| `roles` | 역할 목록 | `hasRole` 평가 |
| `scope` | 액세스 범위(공백 구분) | `hasAuthority('SCOPE_orders.read')` |

스코프 vs 역할

- **역할(roles)**: 사용자의 직무/등급(굵은 권한). 예) `ROLE_ADMIN`
- **스코프(scope)**: 토큰(클라이언트)에 위임된 세부 접근 범위. 예) `orders.read`
- 둘 다 검사할 수 있다. 예: "ADMIN이지만 이 토큰은 `orders.read` 스코프가 없으면 주문 쓰기 불가".

```text
권한 평가 = (역할 통과) AND (스코프 통과) AND (객체 소유권 통과)
```

---

## 권한 검사 위치(Spring Security)

접근 제어는 **여러 계층에서 중첩**으로 적용합니다(심층 방어). 단일 지점에만 의존하지 않습니다.

```text
┌──────────────────────────────────────────────────────────────┐
│ 1) Filter Chain (SecurityFilterChain)                         │
│    - 인증/JWT 검증, URL 패턴 단위 거친 권한                     │
│      requestMatchers("/api/v1/admin/**").hasRole("ADMIN")      │
├──────────────────────────────────────────────────────────────┤
│ 2) Method Security (@PreAuthorize / @PostAuthorize)           │
│    - 서비스/컨트롤러 메서드 단위 세밀한 권한                    │
│      @PreAuthorize("hasAuthority('SCOPE_orders.write')")       │
├──────────────────────────────────────────────────────────────┤
│ 3) Domain / Object-level (소유권·불변식 검사)                 │
│    - order.ownerId == currentUserId 등 객체 수준 인가          │
└──────────────────────────────────────────────────────────────┘
```

### 1) URL 단위 — SecurityFilterChain

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // 토큰 기반 stateless API 가정
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/catalog/**", "/actuator/health").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated() // 거부 기본값
        )
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
    return http.build();
}
```

### 2) 메서드 단위 — @PreAuthorize / @PostAuthorize

```java
@EnableMethodSecurity // @PreAuthorize 활성화
@Configuration
class MethodSecurityConfig {}

@Service
class OrderService {

    @PreAuthorize("hasRole('USER') and hasAuthority('SCOPE_orders.read')")
    public OrderDto getOrder(Long orderId, Long currentUserId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND"));
        // 3) 객체 소유권 검사
        if (!order.getOwnerId().equals(currentUserId)) {
            throw new ForbiddenException("ORDER_FORBIDDEN");
        }
        return OrderDto.from(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long userId) {
        // 감사 로그 필수 (누가 언제 무엇을)
        userRepository.deleteById(userId);
    }
}
```

### 3) 객체 수준(소유권) 검사

URL/역할만으로는 **IDOR(다른 사용자 리소스 접근)** 를 막지 못합니다. 항상 객체 소유권을 확인합니다.

```text
요청: GET /api/v1/orders/777  (현재 토큰 sub=10293, roles=[ROLE_USER])
  1) FilterChain → 인증/ROLE_USER 통과
  2) @PreAuthorize → SCOPE_orders.read 통과
  3) 도메인 검사 → order(777).ownerId(55555) != 10293  →  403 FORBIDDEN
```

> 권한 부족 시 `403`, 인증 실패/만료 시 `401`. 에러 응답 형식은
> [error-response-guide](../api/error-response-guide.md)를 따릅니다.

---

## 클레임 → 권한 변환(JwtAuthenticationConverter)

`roles`/`scope` 클레임을 Spring Security 권한으로 매핑하는 예시.

```java
@Bean
JwtAuthenticationConverter jwtAuthConverter() {
    JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(jwt -> {
        var authorities = new ArrayList<GrantedAuthority>();
        // roles → ROLE_*
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        // scope → SCOPE_*
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.split(" ")) authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
        return authorities;
    });
    return conv;
}
```

---

## 권한 변경/부여 운영 규칙

- 권한 부여/회수는 `ROLE_ADMIN`만 수행하고 **감사 로그**를 남긴다(누가/언제/대상/사유).
- **자기 권한 상향 금지**: 관리자가 자기 자신에게 더 높은 역할을 부여하지 못하게 한다.
- 역할 변경은 **즉시 토큰에 반영되지 않을 수 있음**(Access 토큰 수명 동안 유효). 민감 회수는
  토큰 블랙리스트(`jti`)나 짧은 토큰 수명으로 보완한다.
- 서비스 간 호출(`ROLE_SYSTEM`)은 사용자 토큰과 분리하고 최소 스코프만 부여한다.

---

## 안티패턴(피해야 할 것)

| 안티패턴 | 문제 | 대안 |
| --- | --- | --- |
| 클라이언트가 보낸 `role` 필드 신뢰 | 권한 상승 | 토큰 클레임/DB만 신뢰 |
| URL 권한만 검사, 소유권 미검사 | IDOR | 객체 수준 검사 추가 |
| 역할을 코드 곳곳에 문자열 하드코딩 | 일관성/오타 | 상수화, 매트릭스로 단일 관리 |
| `permitAll`을 광범위하게 사용 | 의도치 않은 노출 | 화이트리스트 최소화, 거부 기본값 |
| 권한 실패를 200으로 무마 | 우회/혼란 | 401/403 명확 반환 |

---

## 체크리스트

- [ ] 역할/권한 매트릭스가 실제 엔드포인트와 일치한다.
- [ ] 모든 보호 엔드포인트가 거부 기본값(`anyRequest().authenticated()`)을 갖는다.
- [ ] 객체 소유권 검사(IDOR 방지)가 누락된 엔드포인트가 없다.
- [ ] 권한은 토큰 클레임/DB 등 서버 신뢰 출처로만 판단한다.
- [ ] 관리자 액션(권한 부여/삭제)에 감사 로그가 있다.
- [ ] 역할 문자열/권한 코드가 상수로 단일 관리된다.
- [ ] 401/403 응답이 [error-response-guide](../api/error-response-guide.md) 형식과 일치한다.
- [ ] 프로젝트 확정 후 실제 역할/스코프 값으로 본 문서를 갱신했다.
