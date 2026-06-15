# 커뮤니티 (게시판 · 동네친구) API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

자유게시판(`FREE`)·동네생활(`NEIGHBORHOOD`) 게시판의 게시글 작성/수정/삭제/목록/상세/검색, 좋아요 토글, 공유 카운트, 댓글, 동네친구 1:1 채팅 시작을 다룬다. 게시글은 텍스트(제목+본문)만 지원하며 사진·동영상은 범위 외다. 채팅 메시지 송수신은 04(채팅) 스펙([04-booking-inquiry-chat](./04-booking-inquiry-chat.md))을 `NEIGHBOR` 카테고리로 재사용한다.

공통 규약:
- 경로 프리픽스 `/api/v1`, 경로는 kebab-case, JSON 필드·쿼리 파라미터는 lowerCamelCase.
- enum은 UPPER_SNAKE_CASE, 날짜·시각은 ISO-8601 UTC(`2026-06-15T08:30:00Z`).
- 인증 헤더 `Authorization: Bearer <accessToken>`. 토큰 만료 `401 TOKEN_EXPIRED`, 미인증 `401 UNAUTHENTICATED`.
- 목록은 **오프셋 페이지네이션**(`page` 0-base, `size` 기본 20·최대 100, `sort`)을 사용한다([api-design-guide](../api-design-guide.md) §4-1). 정렬은 `sort=field,(asc|desc)` 형식을 따른다(§6).
- 공통 에러 코드(`INVALID_INPUT`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `TOO_MANY_REQUESTS` 등)는 [error-response-guide](../error-response-guide.md) §4를 따르며 본 문서에서 재정의하지 않는다.

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/community/posts` | 게시글 목록(게시판별, 정렬·검색·해시태그) | 선택 | 200 |
| POST | `/api/v1/community/posts` | 게시글 작성(제목+본문) | 필수 | 201 |
| GET | `/api/v1/community/posts/me` | 내 게시글 목록 | 필수 | 200 |
| GET | `/api/v1/community/posts/{postId}` | 게시글 상세 | 선택 | 200 |
| PATCH | `/api/v1/community/posts/{postId}` | 게시글 수정(작성자만) | 필수 | 200 |
| DELETE | `/api/v1/community/posts/{postId}` | 게시글 삭제(작성자만, 소프트 삭제) | 필수 | 204 |
| POST | `/api/v1/community/posts/{postId}/like` | 좋아요 토글 | 필수 | 200 |
| POST | `/api/v1/community/posts/{postId}/share` | 공유 카운트 증가 | 필수 | 200 |
| GET | `/api/v1/community/posts/{postId}/comments` | 댓글 목록 | 선택 | 200 |
| POST | `/api/v1/community/posts/{postId}/comments` | 댓글 작성 | 필수 | 201 |
| DELETE | `/api/v1/community/posts/{postId}/comments/{commentId}` | 댓글 삭제(작성자만) | 필수 | 204 |
| POST | `/api/v1/community/posts/{postId}/chat` | 동네친구 1:1 채팅 시작(작성자와) | 필수 | 201 (신규) / 200 (기존 반환) |

## 상세

### GET /api/v1/community/posts — 게시글 목록 / 검색

- 설명: 게시판 종류별 게시글 목록을 최신순/인기순으로 조회한다. `keyword`(제목·본문) 또는 `hashtag`로 검색할 수 있다. 오프셋 페이지네이션.
- 인증: 선택(미인증도 조회 가능). 인증 시 각 항목의 `liked` 여부가 채워질 수 있다.

Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `boardType` | enum | 선택 | (전체) | `FREE` \| `NEIGHBORHOOD`. 생략 시 전체 게시판 |
| `sort` | string | 선택 | `createdAt,desc` | `field,(asc\|desc)` 형식. 최신순 `createdAt,desc`, 인기순 `likeCount,desc`(동점 시 `createdAt,desc`로 보조 정렬). 허용 키: `createdAt`, `likeCount` |
| `keyword` | string | 선택 | — | 제목·본문 부분 일치 검색어 |
| `hashtag` | string | 선택 | — | 해시태그명(`#` 제외). 해당 태그가 달린 글만 |
| `page` | int | 선택 | `0` | 0-base 페이지 번호(`< 0`이면 400) |
| `size` | int | 선택 | `20` | 페이지 크기(1~100, 초과 시 400) |

> `keyword`와 `hashtag`를 동시에 보내면 AND로 적용한다. 정의되지 않은 `sort` 키·정렬 방향, 미정의 `boardType` 값은 무시하지 않고 `400 INVALID_INPUT`으로 응답한다.

성공 Response (200)

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 1024,
        "boardType": "FREE",
        "title": "전입신고 어디서 하나요?",
        "authorNickname": "minho",
        "authorNationality": "VN",
        "createdAt": "2026-06-15T08:30:00Z",
        "likeCount": 12,
        "commentCount": 4,
        "liked": false
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 137,
      "totalPages": 7,
      "hasNext": true
    }
  },
  "error": null
}
```

- 발생 가능한 에러: `INVALID_INPUT`(400, 잘못된 `sort`/`boardType`/`page`/`size`).

### POST /api/v1/community/posts — 게시글 작성

- 설명: 텍스트 게시글(제목+본문)을 작성한다. 작성 즉시 내 게시글 목록에 포함된다.
- 인증: 필수.

Request Body (래퍼 없이)

```jsonc
{
  "boardType": "FREE",        // FREE | NEIGHBORHOOD (필수)
  "title": "전입신고 어디서 하나요?",  // 필수, 1~100자
  "content": "구로구에서 전입신고하려는데...", // 필수, 1~5000자
  "hashtags": ["전입신고", "구로구"]   // 선택, 본문/별도 입력에서 추출된 태그(최대 10개)
}
```

성공 Response (201, `Location: /api/v1/community/posts/{postId}`)

```jsonc
{
  "success": true,
  "data": {
    "postId": 1025,
    "boardType": "FREE",
    "createdAt": "2026-06-15T09:00:00Z"
  },
  "error": null
}
```

- 발생 가능한 에러: `INVALID_INPUT`(400, `boardType`/`title`/`content`/`hashtags` 위반), `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401).

### GET /api/v1/community/posts/me — 내 게시글 목록

- 설명: 인증된 사용자가 작성한 게시글을 최신순으로 조회한다(소프트 삭제 글 제외). 오프셋 페이지네이션.
- 인증: 필수.

Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `boardType` | enum | 선택 | (전체) | `FREE` \| `NEIGHBORHOOD` |
| `page` | int | 선택 | `0` | 0-base 페이지 번호 |
| `size` | int | 선택 | `20` | 페이지 크기(1~100) |

성공 Response (200): `GET /community/posts`와 동일한 목록 항목 구조의 페이지 객체(본인 글이므로 `liked`는 생략 가능).

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 1025,
        "boardType": "FREE",
        "title": "전입신고 어디서 하나요?",
        "authorNickname": "minho",
        "authorNationality": "VN",
        "createdAt": "2026-06-15T09:00:00Z",
        "likeCount": 0,
        "commentCount": 0
      }
    ],
    "page": { "number": 0, "size": 20, "totalElements": 3, "totalPages": 1, "hasNext": false }
  },
  "error": null
}
```

- 발생 가능한 에러: `INVALID_INPUT`(400), `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401).

### GET /api/v1/community/posts/{postId} — 게시글 상세

- 설명: 게시글 상세를 조회한다. 작성자가 탈퇴한 글은 `authorNickname`을 `(탈퇴한 사용자)`, `authorNationality`를 `null`로 마스킹한다.
- 인증: 선택(인증 시 `liked`·`editable` 채워짐).

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

성공 Response (200)

```jsonc
{
  "success": true,
  "data": {
    "postId": 1024,
    "boardType": "FREE",
    "title": "전입신고 어디서 하나요?",
    "content": "구로구에서 전입신고하려는데...",
    "hashtags": ["전입신고", "구로구"],
    "authorId": 77,
    "authorNickname": "minho",
    "authorNationality": "VN",
    "createdAt": "2026-06-15T08:30:00Z",
    "updatedAt": "2026-06-15T08:30:00Z",
    "likeCount": 12,
    "commentCount": 4,
    "shareCount": 2,
    "liked": false,
    "editable": false
  },
  "error": null
}
```

- 발생 가능한 에러: `POST_NOT_FOUND`(404, 없거나 삭제된 글).

### PATCH /api/v1/community/posts/{postId} — 게시글 수정

- 설명: 게시글의 제목·본문·해시태그를 수정한다. **작성자만** 가능. 전송된 필드만 부분 수정한다.
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

Request Body (래퍼 없이, 모두 선택이며 최소 1개 필요)

```jsonc
{
  "title": "전입신고 절차 정리",     // 선택, 1~100자
  "content": "갱신: 구로구청 1층...", // 선택, 1~5000자
  "hashtags": ["전입신고"]          // 선택, 최대 10개
}
```

성공 Response (200): 수정된 게시글 상세(상세 조회와 동일 구조).

- 발생 가능한 에러: `INVALID_INPUT`(400, 위반 필드 또는 본문이 모두 비어 변경 없음), `UNAUTHENTICATED`(401), `FORBIDDEN`(403, 작성자 아님), `POST_NOT_FOUND`(404).

### DELETE /api/v1/community/posts/{postId} — 게시글 삭제

- 설명: 게시글을 소프트 삭제한다. **작성자만** 가능. 삭제된 글의 댓글·좋아요 집계는 함께 정리되며 목록·상세에서 제외된다.
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

성공 Response (204): 본문 없음.

- 발생 가능한 에러: `UNAUTHENTICATED`(401), `FORBIDDEN`(403, 작성자 아님), `POST_NOT_FOUND`(404, 없거나 이미 삭제됨).

### POST /api/v1/community/posts/{postId}/like — 좋아요 토글

- 설명: 게시글 좋아요를 토글한다. 사용자당 최대 1개(유니크 제약). 동시 토글에도 `likeCount`가 정확히 유지된다.
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

Request Body: 없음.

성공 Response (200, 토글 후 현재 상태)

```jsonc
{
  "success": true,
  "data": {
    "liked": true,
    "likeCount": 13
  },
  "error": null
}
```

- 발생 가능한 에러: `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401), `POST_NOT_FOUND`(404).

### POST /api/v1/community/posts/{postId}/share — 공유 카운트 증가

- 설명: 공유 시 게시글의 `shareCount`를 1 증가시킨다(비멱등). 사용자 단위 레이트리밋으로 도배를 방지한다.
- 인증: 필수. (사용자 단위 멱등·레이트리밋 적용을 위해 인증을 요구한다)

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

Request Body: 없음.

성공 Response (200)

```jsonc
{
  "success": true,
  "data": {
    "shareCount": 3
  },
  "error": null
}
```

- 발생 가능한 에러: `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401), `POST_NOT_FOUND`(404), `TOO_MANY_REQUESTS`(429, 레이트리밋 초과).

### GET /api/v1/community/posts/{postId}/comments — 댓글 목록

- 설명: 게시글의 댓글을 작성순(오래된 순)으로 조회한다. 오프셋 페이지네이션. 탈퇴 작성자 댓글은 닉네임 마스킹.
- 인증: 선택.

Path / Query 파라미터

| 이름 | 위치 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `postId` | path | long | 필수 | — | 게시글 식별자 |
| `page` | query | int | 선택 | `0` | 0-base 페이지 번호 |
| `size` | query | int | 선택 | `20` | 페이지 크기(1~100) |

성공 Response (200)

```jsonc
{
  "success": true,
  "data": {
    "content": [
      {
        "commentId": 5001,
        "content": "구로구청 1층에서 하시면 됩니다.",
        "authorId": 88,
        "authorNickname": "jane",
        "authorNationality": "US",
        "createdAt": "2026-06-15T08:40:00Z",
        "editable": false
      }
    ],
    "page": { "number": 0, "size": 20, "totalElements": 4, "totalPages": 1, "hasNext": false }
  },
  "error": null
}
```

- 발생 가능한 에러: `POST_NOT_FOUND`(404), `INVALID_INPUT`(400, 잘못된 `page`/`size`).

### POST /api/v1/community/posts/{postId}/comments — 댓글 작성

- 설명: 게시글에 댓글을 단다. 게시글의 `commentCount`가 1 증가한다.
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |

Request Body (래퍼 없이)

```jsonc
{
  "content": "구로구청 1층에서 하시면 됩니다." // 필수, 1~1000자
}
```

성공 Response (201, `Location: /api/v1/community/posts/{postId}/comments/{commentId}`)

```jsonc
{
  "success": true,
  "data": {
    "commentId": 5002,
    "content": "구로구청 1층에서 하시면 됩니다.",
    "authorNickname": "jane",
    "authorNationality": "US",
    "createdAt": "2026-06-15T09:10:00Z"
  },
  "error": null
}
```

- 발생 가능한 에러: `INVALID_INPUT`(400, `content` 공백/길이 초과), `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401), `POST_NOT_FOUND`(404).

### DELETE /api/v1/community/posts/{postId}/comments/{commentId} — 댓글 삭제

- 설명: 댓글을 소프트 삭제한다. **작성자만** 가능. 게시글의 `commentCount`가 1 감소한다(음수 방지).
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 게시글 식별자 |
| `commentId` | long | 필수 | 댓글 식별자 |

성공 Response (204): 본문 없음.

- 발생 가능한 에러: `UNAUTHENTICATED`(401), `FORBIDDEN`(403, 작성자 아님), `POST_NOT_FOUND`(404, 게시글 부재), `COMMENT_NOT_FOUND`(404, 댓글 부재/이미 삭제).

### POST /api/v1/community/posts/{postId}/chat — 동네친구 1:1 채팅 시작

- 설명: 게시글 작성자와 1:1 채팅방을 시작한다. 채팅방은 04(채팅) 스펙의 `NEIGHBOR` 카테고리로 생성된다. 본인 게시글에는 시작 불가. 이미 방이 있으면 기존 방을 반환(멱등).
- 인증: 필수.

Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | long | 필수 | 채팅을 시작할 대상 게시글(작성자 = 채팅 상대) |

Request Body: 없음.

성공 Response (201 신규 생성 / 200 기존 방, `Location: /api/v1/chat-rooms/{chatRoomId}`)

```jsonc
{
  "success": true,
  "data": {
    "chatRoomId": 9001,
    "category": "NEIGHBOR",
    "peerId": 77,
    "peerNickname": "minho",
    "created": true
  },
  "error": null
}
```

> 채팅 메시지 송수신·조회는 04 채팅 스펙(`/api/v1/chat-rooms/...`)을 재사용한다. `created`가 `false`면 기존 방을 반환한 것이며 status는 `200`이다.

- 발생 가능한 에러: `UNAUTHENTICATED`(401), `TOKEN_EXPIRED`(401), `POST_CHAT_SELF_NOT_ALLOWED`(422, 본인 게시글), `POST_NOT_FOUND`(404), `POST_CHAT_AUTHOR_UNAVAILABLE`(422, 작성자가 탈퇴 등으로 채팅 불가), `POST_CHAT_BLOCKED`(403, 차단 관계 — report 모듈 의존, 확정 필요).

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `TOO_MANY_REQUESTS`, `RESOURCE_NOT_FOUND` 등)는 [error-response-guide](../error-response-guide.md) §4 카탈로그를 따르며 여기서 재정의하지 않는다. 아래는 community 모듈 고유 코드(prefix `POST_`/`COMMENT_`)다.

| code | status | 의미 |
| --- | --- | --- |
| `POST_NOT_FOUND` | 404 | 게시글이 없거나 삭제됨 |
| `COMMENT_NOT_FOUND` | 404 | 댓글이 없거나 삭제됨 |
| `POST_CHAT_SELF_NOT_ALLOWED` | 422 | 본인 게시글에 동네친구 채팅 시작 불가 |
| `POST_CHAT_AUTHOR_UNAVAILABLE` | 422 | 작성자가 탈퇴 등으로 채팅 불가 상태 |
| `POST_CHAT_BLOCKED` | 403 | 차단 관계로 채팅 시작 불가 (report 모듈의 차단 모델 의존 — 확정 필요) |

> 작성자 소유권 위반(타인의 게시글·댓글 수정/삭제)은 공통 `FORBIDDEN`(403)을 사용한다. 채팅방 자체의 메시지/조회 관련 에러(`CHAT_ROOM_NOT_FOUND` 등)는 04 채팅 스펙에서 정의한 코드를 재사용한다.
