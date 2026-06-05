# Backend Architecture Rules

- API 계층, 애플리케이션 계층, 도메인 계층, 인프라 계층의 책임을 분리한다.
- 트랜잭션 경계를 명확히 한다.
- 외부 시스템 연동은 adapter 또는 gateway로 격리한다.
- 실패, 재시도, timeout, idempotency 필요 여부를 검토한다.
- DB schema와 API contract 변경은 문서화한다.
