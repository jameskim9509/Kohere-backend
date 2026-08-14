# US-3-3 — 네이버 장소 검색 및 주변 매물 조회

> 모듈: 매물 등록 · 탐색 · 찜 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/03-listings-favorites.md)
>
> **한 흐름에 두 버전이 섞인다** — 장소 검색 `GET /api/v1/listings/places`는 매물 데이터를 쓰지 않으므로 **v1 그대로 동작**하고, 후속 매물 조회(목록·지도)는 **`/api/v2`가 정본**이다. `/api/v1`의 목록·지도·키워드 검색은 구버전 앱 호환용 `deprecated` 스텁이라 MongoDB에 접근하지 않고 빈 결과만 반환하므로 아래 흐름에 관여하지 않는다.

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant LIST as listing 모듈
    participant NAVER as 네이버 지역 검색 API
    participant DB as MongoDB

    U->>C: 키워드 입력 (예 경희대)
    C->>LIST: GET /api/v1/listings/places?keyword=경희대<br/>(v1 유지 — 매물 데이터를 쓰지 않는다)<br/>(Authorization 선택)
    alt 만료 access token
        LIST-->>C: 401 Unauthorized<br/>error.code=TOKEN_EXPIRED
        C-->>U: 토큰 재발급 안내
    else 키워드 위반 (누락/공백/50자 초과)
        LIST-->>C: 400 Bad Request<br/>error.code=INVALID_INPUT
        C-->>U: 검색어 입력 오류 안내
    else 유효한 검색 요청
        LIST->>NAVER: GET /v1/search/local.json<br/>query=경희대&display=5&start=1&sort=random<br/>X-Naver-Client-Id / X-Naver-Client-Secret
        alt 네이버 호출 또는 응답 처리 실패
            NAVER--xLIST: HTTP 오류, 타임아웃 또는 잘못된 응답
            LIST-->>C: 502 Bad Gateway<br/>error.code=UPSTREAM_ERROR
            C-->>U: 장소 검색 실패 및 재시도 안내
        else 네이버 정상 응답
            NAVER-->>LIST: items[] (title, address, roadAddress, mapx, mapy)
            Note over LIST: mapx/mapy를 WGS84 lng/lat으로 변환<br/>프론트 공개 필드만 최대 5개 반환
            LIST-->>C: 200 OK<br/>data.items[](title, address, roadAddress, lat, lng)
            alt 장소 후보 없음
                C-->>U: 검색 결과 없음 표시<br/>(data.items=[])
            else 사용자가 장소 후보 선택
                U->>C: 원하는 장소 선택
                Note over C: 선택한 lat/lng로 지도 카메라 이동<br/>지도 SDK에서 sw/ne bounds 계산
                par 매물 목록 조회
                    C->>LIST: GET /api/v2/listings<br/>swLat, swLng, neLat, neLng, 필터/정렬/페이지
                    LIST->>DB: bounds 및 필터 기준 매물 조회
                    DB-->>LIST: 매물 페이지
                    LIST-->>C: 200 OK<br/>data.content[], data.page
                and 지도 마커 조회
                    C->>LIST: GET /api/v2/listings/map<br/>swLat, swLng, neLat, neLng, 필터
                    LIST->>DB: bounds 및 필터 기준 좌표 조회
                    DB-->>LIST: 매물 좌표
                    LIST-->>C: 200 OK<br/>data.markers[], data.total
                end
                C-->>U: 선택한 장소 주변의 지도 마커와 매물 목록 표시
            end
        end
    end
```

## 흐름 요약

- `GET /api/v1/listings/places?keyword=...`는 공개 API이며, 백엔드가 네이버 지역 검색 API를 호출해 장소 후보를 최대 5개 반환한다. **매물 데이터를 쓰지 않으므로 조회 계열의 v2 이관 대상이 아니고 v1 경로 그대로 동작한다.** 토큰 없음·위조/형식 오류는 익명으로 통과하고 만료 토큰만 `401 TOKEN_EXPIRED`다. 이 단계에서는 MongoDB 매물을 조회하지 않는다.
- 네이버 원본의 `mapx/mapy`는 백엔드가 WGS84 `lng/lat`으로 변환하고, `title`·`address`·`roadAddress`·`lat`·`lng`만 공개한다. 검색 결과가 없으면 에러가 아닌 `200 OK`와 `data.items=[]`를 반환한다.
- 사용자가 후보를 선택하면 앱이 해당 좌표로 지도 카메라를 이동하고 bounds를 계산한다. 이후 매물 조회는 정본인 `/api/v2/listings`와 `/api/v2/listings/map`에 같은 bounds·필터를 전달해 매물 목록과 지도 마커를 얻는다(같은 흐름 안에서 장소 검색만 v1, 매물 조회는 v2다).
- 키워드 누락·공백·50자 초과는 `400 INVALID_INPUT`, 네이버 HTTP 오류·타임아웃·인증정보 누락·응답 형식 이상은 `502 UPSTREAM_ERROR`로 구분한다.
- 기존 키워드 검색은 `GET /api/v2/listings/search`로 이관돼 그대로 유지하지만, 이 사용자 흐름에서는 사용하지 않는다(`/api/v1/listings/search`는 빈 페이지만 반환하는 `deprecated` 스텁이다).
