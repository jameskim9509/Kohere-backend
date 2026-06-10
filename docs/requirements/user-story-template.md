# User Story Template

> 예시(Spring Boot 기준)입니다. 아래 예시 스토리는 "모임(meeting) 도메인"을 가정한 **샘플**이며,
> 실제 도메인 용어/역할/규칙으로 교체하세요.

## 목적

기능 요구사항을 **사용자 관점의 작은 단위**로 표현하기 위한 표준 양식을 정의한다.
유저 스토리는 구현 명세가 아니라 "누가, 무엇을, 왜 원하는가"에 대한 합의이며,
**검증 가능한 인수 조건**과 짝을 이뤄야 완성된다.

- 스토리는 항상 사용자(역할) 관점에서 작성한다.
- "왜(So that)"가 없는 스토리는 가치 없는 작업일 수 있으므로 반드시 채운다.
- 구현 방법(HOW)이 아니라 요구(WHAT/WHY)를 기술한다.
- 각 스토리는 [acceptance-criteria-template](./acceptance-criteria-template.md)로 검증한다.

---

## 표준 템플릿

```text
제목: [기능 요약]

As a   <역할/페르소나>
I want <원하는 기능/행동>
So that <얻고자 하는 가치/이유>
```

### 메타데이터 항목

| 항목 | 설명 | 예시 |
|---|---|---|
| ID | 추적용 식별자 | `US-MEETING-001` |
| 우선순위 | MoSCoW 등 | Must / Should / Could / Won't |
| 추정 | 상대 추정치 | 3 SP (Story Point) |
| 상태 | 진행 상태 | Draft / Ready / In Progress / Done |
| 관련 NFR | 품질 제약 | [non-functional-requirements](./non-functional-requirements.md) |
| 인수 조건 | AC 링크 | [acceptance-criteria-template](./acceptance-criteria-template.md) |

---

## 작성 규칙

1. **INVEST 원칙**을 만족하는지 점검한다.
   - Independent(독립적), Negotiable(협상 가능), Valuable(가치 있음),
     Estimable(추정 가능), Small(작음), Testable(검증 가능).
2. 역할(As a)은 "사용자"처럼 모호하지 않게 **구체적 페르소나**로 쓴다. (예: "모임 호스트")
3. 하나의 스토리는 하나의 가치에 집중한다. "그리고/또한"이 많으면 분할한다.
4. 화면/버튼/테이블 같은 **구현 디테일은 넣지 않는다**. (그건 AC와 설계에서)
5. 인수 조건은 별도 문서로 분리하되 스토리에서 링크한다.
6. 비기능 제약(응답시간, 권한 등)은 NFR 문서를 참조 링크한다.

---

## 채워진 예시 1 — 모임 생성

```text
ID: US-MEETING-001
제목: 모임 생성

As a   로그인한 모임 호스트
I want 모임 이름, 일시, 정원, 장소를 입력해 모임을 생성하고 싶다
So that 다른 사용자를 초대해 함께 일정을 진행할 수 있다
```

| 항목 | 값 |
|---|---|
| 우선순위 | Must |
| 추정 | 3 SP |
| 상태 | Ready |
| 관련 NFR | 쓰기 API p95 ≤ 400ms ([non-functional-requirements](./non-functional-requirements.md)) |
| 인수 조건 | [acceptance-criteria-template](./acceptance-criteria-template.md) §예시 1 |

비고:
- 정원은 2~100명 범위로 가정(상세 경계값은 AC에서 정의).
- 인증 필요(JWT). 비로그인 사용자는 이 스토리 범위 밖.

---

## 채워진 예시 2 — 모임 참가 신청

```text
ID: US-MEETING-002
제목: 모임 참가 신청

As a   로그인한 일반 사용자
I want 공개된 모임에 참가 신청을 하고 싶다
So that 관심 있는 모임에 참여 의사를 전달할 수 있다
```

| 항목 | 값 |
|---|---|
| 우선순위 | Must |
| 추정 | 2 SP |
| 상태 | Draft |
| 관련 NFR | 정원 초과/중복 신청 동시성 처리 ([non-functional-requirements](./non-functional-requirements.md) §7) |
| 인수 조건 | [acceptance-criteria-template](./acceptance-criteria-template.md) §예시 2 |

---

## 체크리스트

- [ ] 역할(As a)이 구체적 페르소나로 작성되었다.
- [ ] 가치(So that)가 비어 있지 않다.
- [ ] 구현 방법(HOW)이 아닌 요구(WHAT/WHY)로 기술되었다.
- [ ] INVEST 원칙을 만족한다(특히 Small, Testable).
- [ ] [acceptance-criteria-template](./acceptance-criteria-template.md)로 검증 가능한 AC와 연결되었다.
- [ ] 관련 NFR을 참조 링크했다.
- [ ] 프로젝트 확정 후 갱신
