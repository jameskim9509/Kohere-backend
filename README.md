# Backend Base Repository Template

아직 어떤 백엔드 서비스를 만들지 정하지 않은 상태에서 사용할 수 있는 **backend base repository**입니다.

이 저장소는 다음을 미리 갖춰두는 것을 목표로 합니다.

- 백엔드 프로젝트 공통 폴더 구조
- GitHub Issue/PR/CI 기본 템플릿
- 컨벤션/설계/테스트/운영/보안 문서 템플릿
- Claude Code 기반 AI Agent 활용 구조
- 장기 작업을 위한 harness/progress/task-board 구조

## 사용 방법

```bash
git clone <this-template-repository> <project-name>
cd <project-name>
cp CLAUDE.local.example.md CLAUDE.local.md
bash scripts/setup.sh
claude
```

## 프로젝트가 정해진 뒤 먼저 할 일

1. `docs/project/project-brief.md` 작성
2. `docs/requirements/non-functional-requirements.md` 갱신
3. `docs/architecture/system-overview.md` 갱신
4. `.claude/rules/tech-stack.md` 갱신
5. `scripts/quality/check.sh`를 실제 백엔드 스택에 맞게 수정
6. 첫 ADR 작성

## 핵심 디렉터리

```text
.github/     GitHub 협업 템플릿과 Actions
.claude/     Claude Code 설정, rules, skills, agents, harness prompt
docs/        사람이 읽는 문서
harness/     장기 작업 진행 상태와 인수인계
scripts/     품질 검사와 Claude hooks
src/         실제 백엔드 코드 위치
```
