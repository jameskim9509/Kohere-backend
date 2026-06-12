# Task Board Template

> [harness/task-board.md](../../harness/task-board.md)의 **단일 원본(SSOT) 템플릿**이다.
> 새 보드를 구성할 때 아래 "보드 구조"를 복사해 사용한다.

## 사용 규칙

- 카드는 `- [ ] [우선순위] 설명 (담당/메모)` 형식으로 적는다.
- 카드 하나는 "반나절~하루 안에 끝낼 수 있는 단위"로 쪼갠다.
- 칸을 옮길 때 `(YYYY-MM-DD)` 형태로 이동 날짜를 남긴다.
- `Blocked`로 보낼 때는 반드시 **차단 사유**와 **해제 조건**을 적는다.
- 상세 진행 맥락은 [claude-progress.md](../../harness/claude-progress.md)에, 검증 결과는 [evaluator-report.md](../../harness/evaluator-report.md)에 남긴다.

우선순위 표기

| 표기 | 의미 | 예시 |
| --- | --- | --- |
| `[P0]` | 즉시 처리 / 차단 해소 | 빌드 깨짐, 보안 사고 |
| `[P1]` | 이번 사이클 핵심 | 첫 도메인 API 구현 |
| `[P2]` | 있으면 좋음 | 문서 보강, 리팩터링 |

## 보드 구조 (복사해서 사용)

```markdown
## Todo
- [ ] `[P1]` <설명> (담당/메모)

## In Progress
- [ ] `[P1]` <설명> (담당, 시작 YYYY-MM-DD)

## Done
- [x] `[P2]` <설명> (YYYY-MM-DD)

## Blocked
- [ ] `[P1]` <설명>
  - **차단 사유**: <사유>
  - **해제 조건**: <조건>
  - **차단 시작**: YYYY-MM-DD
```
