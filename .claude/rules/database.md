# Database Rules

- migration은 되돌릴 수 있는지 검토한다.
- NOT NULL 컬럼 추가 시 기존 데이터 호환성을 확인한다.
- index 추가 시 lock과 성능 영향을 검토한다.
- transaction boundary를 문서화한다.
