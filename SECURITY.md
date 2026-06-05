# Security Policy

## 민감 정보

다음 정보는 저장소에 커밋하지 않습니다.

- API Key
- Access Token
- Refresh Token
- DB Password
- Private Key
- OAuth Client Secret
- 클라우드 인증 정보
- 운영 서버 접속 정보

## Claude Code 사용 시 주의

Claude Code가 민감 파일을 읽거나 출력하지 않도록 `.claude/settings.json`의 deny 목록을 유지합니다.
