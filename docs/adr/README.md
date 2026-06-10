# Architecture Decision Records

> 예시(Spring Boot 기준)입니다. 인덱스의 ADR 항목과 결정 내용은 **샘플**이며,
> 실제 프로젝트의 결정으로 교체/추가하세요.

## 목적

ADR(Architecture Decision Record)은 **중요한 기술/아키텍처 결정**과 그 배경을
시간순으로 기록하는 경량 문서다. "왜 이렇게 결정했는가"를 보존하여,
나중에 합류한 사람이나 미래의 우리가 결정을 재검토할 수 있게 한다.

- 결정의 결과(코드)만으로는 **이유와 버린 대안**을 알 수 없으므로 ADR로 남긴다.
- 하나의 ADR = 하나의 결정. 결정이 바뀌면 새 ADR로 대체(Supersede)한다.
- 이미 작성된 ADR은 **수정하지 않고 상태만 변경**한다(불변 기록 원칙).

> 작성 절차는 `update-adr` 스킬과 [documentation-convention](../convention/documentation-convention.md)을 따른다.

---

## 언제 ADR을 작성하는가

다음과 같이 **되돌리기 비용이 큰 결정**을 할 때 작성한다.

- 언어/프레임워크/런타임 선택 (예: Spring Boot vs NestJS)
- 데이터 저장소 선택 및 스키마 전략 (예: PostgreSQL 채택, 마이그레이션 도구)
- 인증/인가 방식 (예: 세션 vs JWT)
- 모듈 경계, 통신 방식(동기 REST vs 비동기 메시징)
- 배포/인프라 토폴로지, 멀티테넌시 전략
- 외부 시스템 연동 방식, 장애 격리 전략

사소하고 쉽게 되돌릴 수 있는 결정은 ADR 대상이 아니다(코드 리뷰로 충분).

---

## 상태(Status) 정의

| 상태 | 의미 |
|---|---|
| Proposed | 제안됨. 검토/합의 진행 중 |
| Accepted | 채택됨. 현재 유효한 결정 |
| Deprecated | 더 이상 권장하지 않음(대체 결정은 아직 없음) |
| Superseded by ADR-XXXX | 더 새로운 ADR로 대체됨(반드시 대상 ADR 번호 명시) |
| Rejected | 검토했으나 채택하지 않음(기록 보존용) |

상태 전이 예시:

```text
Proposed ──합의──> Accepted ──새 결정──> Superseded by ADR-0007
                       │
                       └──사용 중단──> Deprecated
```

---

## 파일 네이밍 규칙

```text
docs/adr/NNNN-kebab-case-title.md
```

- `NNNN` : 4자리 0패딩 일련번호. 다음 번호를 사용한다(예: `0002`).
- 제목은 kebab-case 소문자. 동사로 시작하면 결정을 읽기 쉽다.
  - 좋은 예: `0001-use-postgresql.md`, `0002-adopt-jwt-authentication.md`
  - 나쁜 예: `0001-DB.md`, `database-decision.md`(번호 없음)
- `0000-adr-template.md`는 신규 ADR 작성 시 복사해서 쓰는 [템플릿](./0000-adr-template.md)이다.

---

## ADR 인덱스

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [0000](./0000-adr-template.md) | ADR 템플릿 | — (템플릿) | — |
| [0001](./0001-example-use-postgresql.md) | 주 데이터베이스로 PostgreSQL 선택 (작성 예시) | Accepted | 2026-06-09 |
| 0002 | (예시) JWT 기반 인증 채택 | Proposed | TBD |
| 0003 | (예시) 스키마 마이그레이션 도구로 Flyway 채택 | Proposed | TBD |

> 새 ADR을 추가하면 이 표에 한 행을 추가한다(번호/제목/상태/날짜).

---

## 새 ADR 작성 방법

1. [0000-adr-template.md](./0000-adr-template.md)를 다음 번호로 복사한다.
   - 예: `docs/adr/0002-adopt-jwt-authentication.md`
2. H1 제목과 각 섹션(Status ~ Validation)을 채운다.
3. 상태를 `Proposed`로 시작하고, 합의되면 `Accepted`로 변경한다.
4. 위 **ADR 인덱스** 표에 행을 추가한다.
5. 기존 결정을 대체한다면, 옛 ADR의 상태를 `Superseded by ADR-NNNN`으로 바꾼다.

---

## 체크리스트

- [ ] 결정이 "되돌리기 비용이 큰" 것인지 확인했다(아니면 ADR 불필요).
- [ ] 다음 일련번호 + kebab-case 제목으로 파일을 만들었다.
- [ ] Status를 명확히 설정했다(Proposed/Accepted 등).
- [ ] Context/Decision/Alternatives/Consequences/Validation을 모두 채웠다.
- [ ] 버린 대안과 그 이유를 기록했다(ADR의 핵심).
- [ ] ADR 인덱스 표를 갱신했다.
- [ ] Secret/실제 주소를 넣지 않았다.
- [ ] 프로젝트 확정 후 갱신
