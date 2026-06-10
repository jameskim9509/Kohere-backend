# Secrets Management

> 본 문서는 **시크릿(비밀값)**의 저장·주입·로테이션·사고 대응 정책을 정의한다.
> 예시는 **Spring Boot 3.x** 기준으로 작성했으며, 시크릿 저장소(Vault/AWS Secrets Manager 등)는 예시다.
> 스택·인프라가 확정되면 "저장소 종류"와 "주입 방법"만 교체하고, 원칙은 그대로 재사용한다.

관련 문서: [security-policy](./security-policy.md) · 루트 [SECURITY.md](../../SECURITY.md) · [incident-response](../operations/incident-response.md)

---

## 목적

- 시크릿이 **코드/이미지/로그/티켓**에 절대 평문으로 남지 않게 한다.
- 시크릿의 **단일 출처(single source of truth)**와 **주입 경로**를 명확히 한다.
- 유출 시 **즉시 로테이션**할 수 있는 절차를 갖춘다.

---

## 1. 시크릿의 정의와 범위

"시크릿"은 유출 시 보안 사고로 이어지는 모든 비밀값이다.

| 분류 | 예시 |
| --- | --- |
| 인증 자격 증명 | DB 비밀번호, 서비스 계정 비밀번호 |
| API 키/토큰 | 외부 결제·알림·지도 API 키, GitHub Token |
| 암호화 키 | JWT 서명 키, 데이터 암호화 키(KMS), TLS private key |
| 접속 정보 | 운영 DB 접속 URL(자격 포함), 메시지 브로커 자격 |

> 공개 설정값(예: `server.port`, 로그 레벨)은 시크릿이 **아니다** → `.env.example`/`application.yml`에 평문 가능.

---

## 2. 핵심 원칙

1. **커밋 금지**: 시크릿은 Git에 절대 커밋하지 않는다. → [.gitignore](../../.gitignore)가 `.env`, `*secret*`, `*private*` 등을 차단.
2. **단일 출처**: 시크릿은 시크릿 매니저(예: AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager) 또는 배포 플랫폼의 secret store에만 둔다.
3. **런타임 주입**: 애플리케이션은 환경변수 또는 시크릿 매니저 SDK로 **기동 시점에 주입**받는다. 코드/이미지에 박지 않는다.
4. **최소 권한**: 각 환경/서비스는 자신에게 필요한 시크릿에만 접근한다. → [security-policy](./security-policy.md)
5. **로테이션 가능**: 모든 시크릿은 무중단으로 교체할 수 있어야 한다(아래 6절).
6. **로그 비노출**: 시크릿은 로그·에러 응답·트레이스에 남기지 않는다. → [security-policy](./security-policy.md)

---

## 3. 환경별 저장 위치 (예시)

| 환경 | 저장 위치 | 주입 방법 |
| --- | --- | --- |
| 로컬 개발 | 개발자 PC의 `.env`(커밋 금지) | `.env.example`을 복사해 값 채움 |
| CI | CI Secret(예: GitHub Actions Secrets) | 워크플로우 `env`로 주입 |
| dev/staging/prod | 시크릿 매니저(예: AWS Secrets Manager) | 배포 시 환경변수 또는 SDK 조회 |

> 로컬 `.env` 작성은 [.env.example](../../.env.example)을 복사해서 시작한다.

```bash
# 로컬 최초 1회
cp .env.example .env   # .env 는 .gitignore 로 커밋 차단됨
```

---

## 4. 애플리케이션 주입 패턴 (Spring Boot 예시)

### 4.1 환경변수 → 프로퍼티 바인딩

`application.yml`에는 **값이 아니라 참조만** 둔다.

```yaml
spring:
  datasource:
    url: ${DB_URL}            # 실제 값은 환경변수/시크릿 매니저에서 주입
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}  # 평문 금지, 반드시 주입
app:
  jwt:
    secret: ${JWT_SECRET}     # 서명 키 — 시크릿
```

```bash
# 컨테이너/배포 환경에서 주입되는 환경변수 (예시 가짜값)
DB_URL=jdbc:postgresql://db.internal.example.com:5432/meetup
DB_USERNAME=app_meetup
DB_PASSWORD=<INJECTED_BY_SECRET_MANAGER>
JWT_SECRET=<INJECTED_BY_SECRET_MANAGER>
```

### 4.2 시크릿 매니저 직접 조회(선택)

플랫폼 SDK(예: AWS Secrets Manager)로 기동 시 조회해 바인딩한다. 라이브러리(예: `spring-cloud-aws-secrets-manager`) 사용 시 `application.yml`의 `${...}`만 유지하고 출처만 매니저로 바꾼다.

> **금지 패턴**
> ```java
> // ❌ 절대 금지: 코드에 하드코딩
> String jwtSecret = "s3cr3t-key-1234";
> ```

---

## 5. 커밋 사전 차단 (이미 구성됨)

이 저장소는 시크릿 유출을 다층으로 차단한다.

| 장치 | 위치 | 역할 |
| --- | --- | --- |
| `.gitignore` | [.gitignore](../../.gitignore) | `.env`, `*secret*`, `*private*`, 키 파일을 추적 제외 |
| Claude 권한 deny | [.claude/settings.json](../../.claude/settings.json) | 시크릿 경로 Read 차단 |
| 보호 훅 | [protect-sensitive-path.sh](../../scripts/claude-hooks/protect-sensitive-path.sh) | 민감 경로 Read/Edit/Write를 사전 차단 |

> 추가 권장(선택): `gitleaks`/`trufflehog` 같은 시크릿 스캐너를 pre-commit 훅과 CI에 도입.

---

## 6. 로테이션 정책

| 시크릿 | 정기 로테이션(예시) | 즉시 로테이션 트리거 |
| --- | --- | --- |
| DB 비밀번호 | 90일 | 유출 의심, 담당자 퇴사 |
| API 키/토큰 | 90~180일 | 유출 의심, 권한 변경 |
| JWT 서명 키 | 180일(키 ID 병행 운영) | 유출 의심 |

**무중단 로테이션(권장 순서)**

1. 새 시크릿을 시크릿 매니저에 추가(구/신 동시 유효 구간 확보, 예: JWT는 `kid`로 다중 키 검증).
2. 애플리케이션 재배포/리로드로 새 값 사용.
3. 모든 인스턴스가 새 값을 사용하는 것을 확인.
4. 구 시크릿 폐기(revoke).

---

## 7. 유출 사고 대응 (요약)

상세 절차는 [incident-response](../operations/incident-response.md)를 따른다.

1. **차단**: 해당 시크릿 즉시 revoke/disable.
2. **로테이션**: 새 시크릿 발급·주입(6절).
3. **영향 분석**: 접근 로그·감사 로그로 오용 여부 확인.
4. **이력 제거**: Git 히스토리에 커밋됐다면 히스토리 정리(`git filter-repo`) + 강제 푸시 + 팀 공지. (이미 노출된 값은 **반드시 폐기**, 제거만으로 안전해지지 않음)
5. **포스트모템**: 재발 방지책 기록 → [incident-response](../operations/incident-response.md).

---

## 체크리스트

- [ ] 시크릿이 코드/이미지/로그/티켓에 평문으로 없는가
- [ ] 모든 시크릿이 시크릿 매니저(또는 배포 secret store) 단일 출처에 있는가
- [ ] `application.yml`에 값이 아니라 `${...}` 참조만 있는가
- [ ] `.env`가 `.gitignore`로 차단되는가 (커밋 이력 점검 포함)
- [ ] 각 환경/서비스가 최소 권한으로만 시크릿에 접근하는가
- [ ] 무중단 로테이션 절차가 문서화·검증됐는가
- [ ] 유출 대응(차단→로테이션→영향분석) 절차가 준비됐는가
