# Evaluator Report

> 작업 결과를 **요구사항 / 테스트 / 문서 / 보안 / 운영 리스크** 5개 기준으로 검증한 리포트다.
> 대응 프롬프트: [.claude/harness/evaluator-agent-prompt.md](../.claude/harness/evaluator-agent-prompt.md)
> 관련 보드: [task-board.md](./task-board.md) · 진행 맥락: [claude-progress.md](./claude-progress.md)
>
> **이 문서는 "예시 리포트"다.** 아래 내용은 사용법을 보여주기 위한 가상의 평가이며, 실제 평가 시 전부 교체한다.
> 예시 스택은 `Spring Boot 3.x / Java 17 / Gradle / JPA / PostgreSQL / Flyway / JUnit5 + Testcontainers / Spring Security(JWT)`다.

---

## 평가 방법

- 점수는 `0~5점`(5가 가장 좋음), 상태는 `PASS | WARN | FAIL`로 표기한다.
- 각 기준은 **점수 + 상태 + 코멘트 + 권고**를 반드시 포함한다.
- 종합 판정 규칙:
  - 어느 기준이든 `FAIL`이 1개 이상 → 종합 `FAIL` (머지 보류)
  - `FAIL` 없고 `WARN` 1개 이상 → 종합 `WARN` (조건부 머지: 권고 반영)
  - 전부 `PASS` → 종합 `PASS`

평가 기준 요약

| # | 기준 | 핵심 질문 | 근거 문서 |
| --- | --- | --- | --- |
| 1 | 요구사항 | 수용 기준을 충족했는가? | [docs/requirements/acceptance-criteria-template.md](../docs/requirements/acceptance-criteria-template.md) |
| 2 | 테스트 | happy/실패/경계 케이스가 있는가? | [docs/testing/testing-strategy.md](../docs/testing/testing-strategy.md) |
| 3 | 문서 | API/DB/운영 변경이 문서화됐는가? | [docs/convention/documentation-convention.md](../docs/convention/documentation-convention.md) |
| 4 | 보안 | 비밀 노출/인가/입력검증이 안전한가? | [docs/security/security-policy.md](../docs/security/security-policy.md) |
| 5 | 운영 리스크 | 마이그레이션/롤백/관측이 준비됐는가? | [docs/operations/runbook.md](../docs/operations/runbook.md) |

---

## 예시 리포트 #1

- **대상 작업**: `POST /api/v1/users` 회원가입 API 구현 (예시)
- **PR / 브랜치**: `feat/user-signup` (예시)
- **평가일**: `<YYYY-MM-DD>` (예시)
- **평가자**: evaluator-agent
- **종합 판정**: **WARN** (조건부 머지 — 아래 권고 2건 반영 필요)

### 기준별 평가

| 기준 | 점수 | 상태 | 코멘트 |
| --- | :---: | :---: | --- |
| 1. 요구사항 | 5 | PASS | 이메일/비밀번호 검증, 중복 이메일 409 응답까지 수용 기준 충족 |
| 2. 테스트 | 4 | PASS | happy + 중복이메일 + 형식오류 케이스 존재. Testcontainers로 DB 통합 테스트 포함 |
| 3. 문서 | 3 | WARN | API 스펙 추가됐으나 에러 응답 표가 [error-response-guide](../docs/api/error-response-guide.md) 형식과 불일치 |
| 4. 보안 | 4 | PASS | 비밀번호 BCrypt 해시, 응답에 password 미노출. 단 로그에 email 평문 출력 1건 |
| 5. 운영 리스크 | 3 | WARN | Flyway `V2__create_users.sql` 추가됨. 롤백 스크립트/절차 미기재 |

### 상세 코멘트와 권고

**1. 요구사항 — 5점 / PASS**

- 코멘트: 수용 기준(유효 이메일·8자 이상 비밀번호·중복 차단)을 모두 만족. 응답이 `201 Created` + `Location` 헤더 규약 준수.
- 권고: 없음.

**2. 테스트 — 4점 / PASS**

- 코멘트: 아래처럼 경계/실패 케이스가 잘 분리됨.

```java
@Test
void 중복_이메일이면_409를_반환한다() { /* ... */ }

@Test
void 비밀번호가_8자_미만이면_400을_반환한다() { /* 경계값: 7자 */ }
```

- 권고: 동시 가입 경합(같은 이메일 동시 요청) 케이스 1건 추가 권장. 유니크 제약 위반 → 409 매핑 검증.

**3. 문서 — 3점 / WARN**

- 코멘트: 에러 응답이 가이드의 표준 envelope와 다름. 가이드 형식은 다음과 같다.

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 409,
  "code": "USER_EMAIL_DUPLICATED",
  "message": "이미 사용 중인 이메일입니다.",
  "path": "/api/v1/users"
}
```

- 권고(필수): 구현 응답을 위 envelope로 통일하고 [docs/api/error-response-guide.md](../docs/api/error-response-guide.md)에 `USER_EMAIL_DUPLICATED` 코드 등록.

**4. 보안 — 4점 / PASS**

- 코멘트: 자격증명 처리는 안전. 다만 `log.info("signup: {}", email)`로 개인정보(이메일) 평문 로깅 발견.
- 권고: 이메일 마스킹(`u***@example.com`) 또는 로깅 제거. 근거: [docs/security/security-policy.md](../docs/security/security-policy.md).

**5. 운영 리스크 — 3점 / WARN**

- 코멘트: 마이그레이션은 추가됐으나 되돌리기 절차가 없음. NOT NULL 컬럼 추가는 무중단 배포 시 주의 필요.
- 권고(필수): [docs/database/migration-policy.md](../docs/database/migration-policy.md)에 따라 롤백 가능성 검토 결과를 PR에 기재하고, [docs/operations/deployment-guide.md](../docs/operations/deployment-guide.md)에 절차 링크.

### 머지 전 체크리스트 (이 리포트 기준)

- [ ] (필수) 에러 응답 envelope 표준화 + 에러 코드 문서 등록
- [ ] (필수) 마이그레이션 롤백 절차 기재
- [ ] (권장) 이메일 평문 로깅 제거
- [ ] (권장) 동시 가입 경합 테스트 추가

---

## 예시 리포트 #2 (간단형 — FAIL 사례)

- **대상 작업**: `GET /api/v1/users/{id}` 조회 (예시)
- **종합 판정**: **FAIL** (머지 보류)

| 기준 | 점수 | 상태 | 핵심 코멘트 |
| --- | :---: | :---: | --- |
| 1. 요구사항 | 4 | PASS | 조회 응답 스키마 충족 |
| 2. 테스트 | 1 | FAIL | 인가 실패(타인 리소스 접근) 테스트 없음 |
| 3. 문서 | 3 | WARN | 권한 규칙 미문서화 |
| 4. 보안 | 1 | FAIL | 본인 외 사용자도 조회 가능 — 수평 권한 상승(IDOR) |
| 5. 운영 리스크 | 4 | PASS | 스키마 변경 없음 |

- 차단 사유: 보안 `FAIL`(IDOR) — 다른 사용자의 `id`로 조회 시 200 반환됨.
- 권고: 소유권 검증 추가 후 재평가. 근거: [docs/security/access-control.md](../docs/security/access-control.md).
- 후속: [task-board.md](./task-board.md) Blocked로 이동, 사유/해제 조건 기록.

---

## (예시) 새 리포트 작성 템플릿 — 복사해서 사용

```markdown
## 리포트 #N

- 대상 작업: <설명>
- PR / 브랜치: <branch>
- 평가일: <YYYY-MM-DD>
- 종합 판정: PASS | WARN | FAIL

| 기준 | 점수 | 상태 | 코멘트 |
| --- | :---: | :---: | --- |
| 1. 요구사항 |  |  |  |
| 2. 테스트 |  |  |  |
| 3. 문서 |  |  |  |
| 4. 보안 |  |  |  |
| 5. 운영 리스크 |  |  |  |

### 권고
- (필수) ...
- (권장) ...
```
