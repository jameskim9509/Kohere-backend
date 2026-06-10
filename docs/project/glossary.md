# Glossary

## 목적

이 문서는 프로젝트에서 사용하는 도메인 용어를 정의해 팀/코드/문서 사이의 표현을 통일한다.
코드의 클래스명, API 필드명, DB 컬럼명은 가능하면 아래 영문 표기를 따른다(Ubiquitous Language).

> 예시(Spring Boot 기준): 아래 용어는 가상의 도메인 **"모임/일정 공유 서비스(Meetup)"** 를 예시로 작성했다.
> 실제 도메인 용어는 프로젝트 확정 후 교체한다. 관련 맥락은 [project-brief](project-brief.md) 참고.

---

## 용어집

| 용어 | 영문 | 정의 | 비고 |
| --- | --- | --- | --- |
| 모임 | Meeting | 호스트가 만드는 사람들의 그룹. 여러 일정을 가질 수 있는 컨테이너. | 애그리거트 루트 |
| 호스트 | Host | 모임을 생성하고 관리하는 사용자. 일정 생성/수정/삭제 권한 보유. | 모임당 1명 이상, 위임 가능 |
| 참여자 | Participant | 초대코드로 모임에 합류한 사용자. 일정에 RSVP 가능. | 호스트도 참여자에 포함 |
| 사용자 | User | 가입한 계정 주체. 호스트/참여자 역할을 가질 수 있음. | 인증 단위 |
| 초대코드 | Invite Code | 모임 참여를 위해 발급되는 코드 문자열. 만료/재발급 가능. | 예: `ABCD-1234` |
| 일정 | Event | 모임 안의 단일 약속(일시/장소). 참석 여부 집계 대상. | 변경 이력 추적 |
| 참석 여부 | RSVP | 일정에 대한 참여자의 응답 상태. | 값: 참석/불참/미정 |
| 참석 상태 | Attendance Status | RSVP의 구체 값 enum. | `ATTENDING` / `DECLINED` / `PENDING` |
| 알림 | Notification | 일정 변경/리마인더를 참여자에게 전달하는 메시지. | 푸시/이메일 채널 |
| 리마인더 | Reminder | 일정 시작 전 자동 발송되는 알림. | 예: 1시간 전 |
| 액세스 토큰 | Access Token | 인증 후 발급되는 단기 JWT. API 호출 시 사용. | 예시 만료 15분 |
| 리프레시 토큰 | Refresh Token | 액세스 토큰 재발급용 장기 토큰. | 예시 만료 14일 |

---

## 용어 사용 규칙

- 한 개념에 하나의 영문 표기만 사용한다(예: `Participant`와 `Member`를 혼용하지 않는다).
- enum 값은 대문자 SNAKE_CASE로 통일한다(`ATTENDING`).
- 새 용어가 생기면 코드 작성 전 이 표에 먼저 추가한다.
- 약어는 최초 1회 풀어 쓰고 괄호로 약어를 병기한다(예: RSVP(Répondez s'il vous plaît, 참석 여부)).

## 관련 문서

- [project-brief](project-brief.md) — 도메인 배경
- [database-design](../database/database-design.md) — 테이블/컬럼 명명
- [api-design-guide](../api/api-design-guide.md) — API 필드 명명
