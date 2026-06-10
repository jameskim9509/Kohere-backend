# Progress Template

> [harness/claude-progress.md](../../harness/claude-progress.md)의 **단일 원본(SSOT) 템플릿**이다.
> 새 세션을 기록할 때 아래 "세션 블록"을 복사해 `claude-progress.md` **맨 위**(최신이 위)에 붙이고 채운다.

## 기록 규칙

- 새 세션은 **맨 위**에 추가한다(최신이 위).
- `Status`는 `진행중 | 완료 | 차단` 중 하나로 적는다.
- 각 세션은 `Date / Goal / Status` 헤더 + `Completed / In Progress / Blocked / Decisions / Next Steps`로 구성한다.
- 중요한 기술 결정은 `Decisions`에 요약하고, 정식 결정은 [docs/adr/](../../docs/adr/)에 ADR로 남긴다.
- 날짜는 임의로 지어내지 말고 실제 값(`YYYY-MM-DD`)으로 적는다.

## 세션 블록 (복사해서 사용)

```markdown
## Session N — <한 줄 제목>

- **Date**: <YYYY-MM-DD>
- **Goal**: <이번 세션의 목표 한 줄>
- **Status**: 진행중 | 완료 | 차단

### Completed
- <끝낸 일>

### In Progress
- <진행 중인 일과 현재 위치>

### Blocked
- <차단 항목 + 사유 + 해제 조건> (없으면 "없음")

### Decisions
- <내린 결정과 근거> (정식 결정은 docs/adr/ 에 ADR 추가)

### Next Steps
- [ ] <다음에 할 일>
```
