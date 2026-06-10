# Pull Request Convention

> **예시(Spring Boot 기준) 안내**
> PR 규칙은 스택과 무관하게 동일합니다. 아래 검증 명령 예시(`./gradlew test` 등)만 프로젝트 스택에 맞게 교체하세요.
> 연계 문서: [branch-convention](./branch-convention.md), [commit-convention](./commit-convention.md), [code-style](./code-style.md)
> 템플릿: [.github/PULL_REQUEST_TEMPLATE.md](../../.github/PULL_REQUEST_TEMPLATE.md) · 코드 소유자: [.github/CODEOWNERS](../../.github/CODEOWNERS)

## 목적

PR의 제목/본문/크기/리뷰/머지 조건을 통일해 **리뷰를 빠르고 안전하게** 만든다.
작은 PR + 명확한 컨텍스트 = 적은 버그, 빠른 머지.

---

## 1. PR 제목 형식

커밋과 동일하게 [Conventional Commits](./commit-convention.md)를 따른다. Squash merge 시 그대로 최종 커밋이 된다.

```text
<type>(<scope>): <subject>

예) feat(auth): add jwt login api
    fix(order): prevent double cancel on retry
```

| 좋은 예 | 나쁜 예 |
| --- | --- |
| `feat(user): support nickname change` | `Update`, `작업함`, `WIP` |
| `fix(order): fix race condition on cancel` | `버그수정` |

---

## 2. PR 본문 형식

본문은 저장소 템플릿([.github/PULL_REQUEST_TEMPLATE.md](../../.github/PULL_REQUEST_TEMPLATE.md))을 그대로 사용한다. 구성은 다음과 같다.

```markdown
## 변경 목적
- 왜 이 변경이 필요한지 (배경/문제)

## 주요 변경 사항
- 핵심 변경점 bullet
- 무엇을 어떻게 바꿨는지

## 테스트 결과
- [ ] 테스트 실행함
- [ ] 테스트 추가/수정함
- [ ] 테스트 불필요 사유 작성함
```bash
# 실행한 명령어 (예시)
./gradlew test
```

## 문서 갱신
- [ ] 문서 갱신함
- [ ] 문서 변경 불필요

## 리뷰 포인트
- 집중해서 봐야 할 부분

## 리스크
- 장애/롤백/마이그레이션 영향
```

작성 팁:
- **변경 목적**은 "무엇을"이 아니라 "왜"를 적는다.
- 관련 이슈는 `Closes #123`으로 연결한다.
- UI/응답 변화는 before/after 예시(JSON/스크린샷)를 첨부한다.
- DB 변경은 마이그레이션/롤백 가능성을 [.claude/rules/database.md](../../.claude/rules/database.md) 기준으로 적는다.

---

## 3. PR 크기 가이드

작은 PR을 강하게 권장한다. 리뷰 품질과 머지 속도가 크기에 반비례한다.

| 변경 라인 수(목안) | 평가 | 권장 |
| --- | --- | --- |
| ~200 LOC | 이상적 | 그대로 진행 |
| 200~400 LOC | 허용 | 가능하면 분할 |
| 400~800 LOC | 큼 | 분할 권장, 리뷰 포인트 명확화 |
| 800 LOC~ | 과도 | **분할 필수** (또는 사유 명시) |

- 리팩터링과 기능 변경은 **별도 PR**로 분리한다.
- 대규모 변경은 작은 PR 여러 개 + 추적용 트래킹 이슈로 쪼갠다.
- 생성 파일/포맷팅 대량 변경은 별도 PR로 분리해 리뷰 노이즈를 줄인다.

---

## 4. 리뷰어 지정

| 항목 | 규칙 |
| --- | --- |
| 기본 리뷰어 | [.github/CODEOWNERS](../../.github/CODEOWNERS) 규칙에 따라 자동 지정 |
| 최소 승인 수 | 1명 이상(중요 변경은 2명) |
| 도메인 전문가 | 보안/DB/외부 연동 변경 시 해당 영역 담당자 포함 |
| 작성자 self-approve | 금지 |

> CODEOWNERS 예시: `/docs/ @owner`, `/.github/ @owner`. 경로별 소유자를 두어 관련 변경 시 자동 리뷰 요청이 가도록 한다.

---

## 5. 리뷰 진행 규칙

- 리뷰 코멘트는 **근거와 대안**을 함께 제시한다(단순 지적 지양).
- `nit:`(사소), `question:`(질문), `blocker:`(머지 차단) 등 프리픽스로 의도를 명확히 한다.
- 작성자는 모든 코멘트에 응답(반영 또는 사유)한다.
- 토론이 길어지면 동기 논의 후 결론을 PR에 기록한다.

---

## 6. 머지 조건 (Merge Gate)

아래를 **모두** 충족해야 머지할 수 있다.

- [ ] CI 통과(빌드/테스트/정적 분석) — 예시: `./gradlew check`
- [ ] 필수 리뷰 승인 충족(1명 이상)
- [ ] 모든 리뷰 코멘트 resolve
- [ ] `main` 최신 반영 + 충돌 없음
- [ ] 템플릿의 체크리스트 충족
- [ ] Secret/credential 미포함

머지 전략은 [branch-convention](./branch-convention.md) §4의 **Squash and merge**를 기본으로 하며, 병합 후 브랜치는 삭제한다.

```text
PR 생성 → CI 통과 → 리뷰 승인 → 코멘트 resolve → main 최신화 → Squash merge → 브랜치 삭제
```

---

## 7. 템플릿 체크리스트 (PR 본문 하단)

템플릿에 포함된 최종 체크리스트:

- [ ] 변경 범위가 명확하다
- [ ] 불필요한 파일 변경이 없다
- [ ] Secret이 포함되지 않았다
- [ ] 관련 문서가 갱신되었다
- [ ] (도구 생성 코드가 있다면) 사람이 검토했다

> 셀프 리뷰: PR을 올리기 전에 작성자가 먼저 diff를 처음부터 끝까지 읽는다.

---

## 체크리스트

- [ ] PR 제목이 Conventional Commits 형식인가
- [ ] 본문이 저장소 PR 템플릿 구조를 따르는가(목적/변경/테스트/문서/리스크)
- [ ] PR 크기가 적절한가(과도하면 분할했는가)
- [ ] 적절한 리뷰어(CODEOWNERS 포함)가 지정되었는가
- [ ] 머지 조건(CI/승인/코멘트 resolve/충돌 없음/secret 없음)을 충족했는가
- [ ] 머지 후 브랜치 삭제 및 Squash 전략을 따랐는가
