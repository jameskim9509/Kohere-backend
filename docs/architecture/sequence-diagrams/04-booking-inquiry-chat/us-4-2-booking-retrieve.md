# US-4-2 — 내 예약 조회(목록·단건 상세)

> 모듈: 매물 예약(신청) · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/04-booking-inquiry-chat.md)

## 목록 조회 — GET /api/v1/bookings

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant BOOK as booking 모듈
    participant USER as user::api
    participant XDB as 저장소(booking, 추후 결정)

    U->>C: 내 예약 탭 진입
    C->>SEC: GET /api/v1/bookings?page=0&size=20<br/>Authorization: Bearer <token>
    Note over SEC: JWT 검증 (서명·만료·클레임)

    alt 토큰 없음/만료/위조
        SEC-->>C: 401 UNAUTHENTICATED (만료 시 TOKEN_EXPIRED)
        C-->>U: 로그인 필요/세션 만료 안내
    else 토큰 유효
        SEC->>BOOK: 인증된 요청 전달 (tenantId)
        BOOK->>USER: findBlockedUserIds(tenantId)
        USER-->>BOOK: 내가 차단한 상대 userId 목록(빈 목록 가능)
        Note over BOOK: 요청자 본인 예약만 조회<br/>삭제·차단 제외: tenant_deleted_at IS NULL<br/>AND landlord_id NOT IN (차단 목록)<br/>createdAt 내림차순 정렬<br/>오프셋 페이지네이션(page/size)
        BOOK->>XDB: 예약 페이지 조회<br/>(tenantId=본인, 삭제·차단 제외, createdAt desc, page/size)
        XDB-->>BOOK: 예약 페이지(빈 목록 가능)
        BOOK-->>C: 200 OK<br/>data.content[]: bookingId, 매물요약(listingId·title·thumbnailUrl),<br/>roomOfferId, moveInDate, contractPeriod, status, createdAt<br/>data.page: number/size/totalElements/totalPages/hasNext
        C-->>U: 예약 목록 표시(빈 목록도 정상)
    end
```

## 단건 상세 조회 — GET /api/v1/bookings/{bookingId}

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant BOOK as booking 모듈
    participant XDB as 저장소(booking, 추후 결정)
    participant LIST as listing::api
    participant USER as user::api

    U->>C: 예약 항목 선택(상세)
    C->>SEC: GET /api/v1/bookings/{bookingId}<br/>Authorization: Bearer <token>
    Note over SEC: JWT 검증 (서명·만료·클레임)

    alt 토큰 없음/만료/위조
        SEC-->>C: 401 UNAUTHENTICATED (만료 시 TOKEN_EXPIRED)
        C-->>U: 로그인 필요/세션 만료 안내
    else 토큰 유효
        SEC->>BOOK: 인증된 요청 전달 (tenantId)
        BOOK->>USER: findBlockedUserIds(tenantId)
        USER-->>BOOK: 내가 차단한 상대 userId 목록(빈 목록 가능)
        BOOK->>XDB: bookingId로 예약 조회<br/>(tenantId=본인, 삭제·차단 제외)
        XDB-->>BOOK: 예약(tenantId·listingId·roomOfferId·moveInDate·contractPeriod·status·createdAt) 또는 없음

        alt 예약 없음 또는 본인 예약 아님 또는 삭제·차단으로 숨김
            Note over BOOK: 존재 여부 비노출 — 본인 예약이 아니면 404로 통일<br/>내가 삭제(US-4-7)했거나 상대를 차단(US-4-8)한 예약도 404
            BOOK-->>C: 404 BOOKING_NOT_FOUND
            C-->>U: 예약을 찾을 수 없음 안내
        else 본인 예약 (정상)
            Note over BOOK: 스냅샷 없음 — 조회 시점 실시간 조인(가격·성명)
            BOOK->>LIST: 매물요약·roomOffer pricing 조회(listingId, roomOfferId)
            LIST-->>BOOK: title·thumbnailUrl·주소·roomOffer name·deposit·monthlyRent
            BOOK->>USER: getUserName(tenantId)
            USER-->>BOOK: tenantName(예약자 성명)
            Note over BOOK: totalAmount = deposit + monthlyRent × contractPeriod<br/>(contractPeriod=계약 개월수 정수, 관리비 제외)
            BOOK-->>C: 200 OK<br/>data: 매물정보(listingId·title·thumbnailUrl·주소·roomOffer name),<br/>createdAt(예약 일시), moveInDate(타겟 입주일), contractPeriod,<br/>tenantName, deposit, totalAmount
            C-->>U: 예약 상세·예상 비용 표시
        end
    end
```

## 흐름 요약

- 보호 엔드포인트이므로 **공통 보안 필터(SEC)** 가 컨트롤러 앞단에서 `Authorization: Bearer <token>`의 JWT(서명·만료·클레임)를 검증한 뒤 인증된 요청(tenantId)을 **booking 모듈**로 전달한다. 토큰이 없거나 만료/위조면 필터가 `401 UNAUTHENTICATED`(만료 시 `TOKEN_EXPIRED`)로 막는다.
- **목록(`GET /api/v1/bookings`)**: booking 모듈이 **저장소**에서 요청자 본인(`tenantId`) 예약만 `createdAt` 내림차순으로 오프셋 페이지네이션(api-design-guide §4-1)해 조회한다. 각 항목에 `bookingId`·매물 요약(`listingId`·`title`·`thumbnailUrl`)·`roomOfferId`·`moveInDate`·`contractPeriod`·`status`·`createdAt`가 담기고 `page` 메타(`number`/`size`/`totalElements`/`totalPages`/`hasNext`)를 포함해 `200 OK`로 응답한다. 예약이 하나도 없으면 빈 `content: []`·`totalElements: 0`을 반환(에러 아님)하며, 타인 예약은 목록에 절대 포함되지 않는다.
- **단건 상세(`GET /api/v1/bookings/{bookingId}`)**: booking 모듈이 예약을 조회해 **요청자 본인 예약인지 먼저 확인**하고, 예약이 없거나 본인 예약이 아니면 존재 여부를 노출하지 않도록 `404 BOOKING_NOT_FOUND`로 **통일**한다.
- **삭제·차단 제외 필터(목록·상세 공통)**: 두 읽기 경로 모두 `tenant_deleted_at IS NULL`(내가 삭제하지 않은 예약, [US-4-7](us-4-7-booking-delete.md))과 `landlord_id NOT IN (내가 차단한 상대, [US-4-8](us-4-8-booking-block.md))`를 만족하는 행만 본다. 필터는 응용 계층 후처리가 아니라 **술어를 저장소 조회로 내려서** 적용한다 — 페이지네이션이 별도 count 쿼리로 `totalPages`/`hasNext`를 유도하므로 후처리로 걸러내면 페이지 메타가 어긋난다. 차단 목록(`user::api` `findBlockedUserIds`)은 `user` 모듈이 소유한 `user_blocks`에서 오며, booking이 그 테이블을 직접 조인하지 않고 **애플리케이션 레벨 조인**으로 결합한다([ADR-0002](../../../adr/0002-inter-module-communication-via-events.md)·[ADR-0005](../../../adr/0005-polyglot-persistence.md)). 차단이 0건이면 `NOT IN`이 모든 행을 지우지 않도록(`NOT IN ()`은 문법 오류, `NOT IN (null)`은 `UNKNOWN`이라 **모든 행이 사라진다**) 어댑터 내부에서 빈 목록을 sentinel `-1L` 한 건으로 정규화한다 — `users.id`는 `BIGINT AUTO_INCREMENT`라 `-1`이 실제 식별자와 충돌할 수 없다. 숨겨진 예약은 상세에서도 `404 BOOKING_NOT_FOUND`로 통일되지만, **신고([US-4-9](us-4-9-booking-report.md))는 이 필터를 타지 않아** 삭제·차단한 예약도 접수된다(증거 보존).
- **가격·매물 정보·성명은 예약에 스냅샷 저장하지 않고 조회 시점에 실시간 조인**한다(cross-store 조인 금지 → 애플리케이션 레벨 조합, [ADR-0005](../../../adr/0005-polyglot-persistence.md)) — **`listing::api`** 로 `(listingId, roomOfferId)`의 매물 요약(제목·썸네일·주소·방 상품명)과 `pricing`(보증금 `deposit`·월세 `monthlyRent`)을, **`user::api`(`getUserName`)** 로 예약자 성명(`tenantName`)을 가져온다(둘 다 신규 공개 조회 메서드 필요). **총 금액 `totalAmount` = `deposit` + `monthlyRent` × `contractPeriod`**(`contractPeriod`는 계약 개월수 정수, **관리비 `maintenanceFee` 제외**)로 계산해, 매물 정보·예약 일시(`createdAt`)·타겟 입주일(`moveInDate`)·계약기간(`contractPeriod`)·예약자 성명·보증금·총 금액을 `200 OK`로 내려준다.
- **정합성**: 예약 이후 방 상품 가격이 바뀌어도 스냅샷이 아니라 **현재 가격 기준**으로 보증금·총 금액을 계산한다. 예약한 방 상품이 이후 비공개/삭제된 경우 매물 정보·가격 파트의 표기 정책(매물 필드 `null`/tombstone vs 별도 상태 코드)은 **(확인 필요)** — 예약 코어 내역(날짜·계약기간·상태)은 유지한다.

> 신설 의존: 신규 에러코드 `BOOKING_NOT_FOUND`(404), `listing::api`(매물 요약·`roomOffer` 가격 조회)·`user::api`(`getUserName`, 차단 필터용 `findBlockedUserIds(blockerId)`) 공개 메서드, `bookings.tenant_deleted_at` 컬럼과 삭제·차단 술어를 반영한 조회, 그리고 `booking → {listing::api, user::api}` 의존 화이트리스트(`booking/package-info.java`) 추가가 선행돼야 한다([ADR-0002](../../../adr/0002-inter-module-communication-via-events.md)).
