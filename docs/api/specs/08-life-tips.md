# 생활 팁 (주제별 생활 정보) API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md) — 8. 생활 팁(US-8-1/US-8-2/US-8-3)

## 개요

외국인이 한국 생활에 필요한 정보를 **주제(topic)** 별로 묶어 조회하는 **읽기 전용 큐레이션** 기능이며, **로그인 없이(게스트) 조회할 수 있고 역할 제한도 없다**(issue #181). 사용자는 먼저 주제 목록을 보고(US-8-1), 특정 주제를 고르면 그 주제에 속한 생활 팁(**제목 · 내용 · 사진**) 전체 리스트를 받는다(US-8-2). 한 주제에는 여러 개의 제목-내용-사진 항목이 들어갈 수 있다(주제 : 팁 = **1 : N**). 콘텐츠는 운영이 시드로 적재하는 큐레이션 콘텐츠이며 사용자 작성·수정·좋아요·신고가 없다(UGC인 커뮤니티(05)와 구분된다).

- **번역이 이 기능의 바탕이다**: 주제명(`name`)·주제 설명(`shortDescription`·`longDescription`)·제목(`title`)·내용(`content`) 표시 텍스트는 **사용자 표시 언어**로 번역해 내려준다(US-8-3). 진단 i18n과 **완전히 동일한 전략**을 재사용하며 별도 메커니즘을 만들지 않는다([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md), US-2-6). 표시 문자열은 도큐먼트 안 **인라인 언어-키 맵**(`{ "en": …, "ja": …, "ko": … }`)으로 임베드되고, 서버가 **로그인 사용자에 한해** `user` 모듈 공개 query `getLanguage(userId)`로 취득한 언어 키로 문자열을 골라 조립하며, 해당 언어 키가 없으면 **영어(`en`)로 폴백**한다(에러 아님). **게스트(비로그인)는 `getLanguage`를 호출하지 않고 표시 언어가 `en` 고정**이다.
- **식별자·이미지·사진은 언어 무관 불변**: 주제·팁의 식별자(`code`/`id`), 주제 이미지(`LifeTipTopic.imageUrl`·`backgroundImageUrl`), 팁 사진(`LifeTip.imageUrl`)은 언어와 무관하게 동일하고, 표시 텍스트만 언어별이다. 응답 스키마도 언어와 무관하게 동일하다(서버가 언어 문자열만 채운다).
- **비페이지**: 두 목록 모두 **고정·소규모 카탈로그**라 페이지네이션 없이 전체 배열을 한 번에 반환한다(페이지 객체 없음 — api-design-guide §4 목록 규약 미적용, 신고 사유 목록 US-7-3과 동일 성격).
- **읽기 전용**: 생활 팁 도메인은 조회만 제공하며, 발행/구독하는 도메인 이벤트가 없다(1차 MVP 이후 홈 부가 기능).
- **저장소**: 문서형·가변 스키마·언어-키 맵 임베드 특성상 **MongoDB**에 둔다([ADR-0005](../../adr/0005-polyglot-persistence.md) 폴리글랏, [ADR-0028](../../adr/0028-diagnosis-questions-catalog-store.md) 진단 카탈로그 저장 방식과 정합). 카탈로그는 Mongock `@ChangeUnit`(모듈별)로 `lifeTipTopics`/`lifeTips`에 시드 적재한다(진단 카탈로그 시드와 동일 방식, [ADR-0032](../../adr/0032-mongodb-migration-runner.md)).

공통 규약:

- 경로 프리픽스 `/api/v1`, 경로는 kebab-case, JSON 필드는 lowerCamelCase.
- 식별자·enum 키는 UPPER_SNAKE 문자열(주제 `code`), 시각은 UTC ISO-8601, 금액은 KRW 정수. 본 도메인에는 시각·금액 필드가 없다.
- 모든 응답은 공통 래퍼 `{ success, data, error }`([ADR-0004](../../adr/0004-api-response-envelope.md)). 인증은 `Authorization: Bearer <accessToken>`이며 본 도메인에서는 **선택**이다(헤더가 없으면 게스트).
- 표시 언어 결정은 `Accept-Language` 헤더·토큰 클레임에 의존하지 않는다 — **로그인 사용자는 자신이 고른 표시 언어(`users.lang`)가 있으면 그 값, 없으면 `en`**이고 **게스트는 `en` 고정**이다. 상세는 아래 [i18n(번역) 절](#i18n번역--adr-0029-재사용)을 참조한다.

### 핵심 개념·리소스

| 개념 | 형태 | 설명 |
| --- | --- | --- |
| 주제 `code` (`LifeTipTopic._id`) | UPPER_SNAKE string | 언어 무관 불변 식별자. 예: `MOVING_IN`, `ADMINISTRATION`, `TRANSPORT`, `FINANCE`, `HOUSING`. 노출 순서(`order`) 오름차순으로 정렬된다. US-8-2에서 특정 주제의 팁을 지정하는 path 키로 쓰인다 |
| 팁 `id` (`LifeTip._id`) | ObjectId hex string | 언어 무관 불변 팁 식별자. 하나의 주제(`topicCode`)에 속한다(주제 : 팁 = 1 : N) |
| 표시 텍스트 `name`/`title`/`content` | 번역 문자열 | 도큐먼트 안 인라인 언어-키 맵에서 사용자 언어 키를 골라(없으면 `en` 폴백) 채운 결과. 응답에는 언어 문자열 하나만 담긴다 |
| 주제 설명 `shortDescription`/`longDescription` | 번역 문자열 | 주제의 짧은 설명(홈 카드용)·긴 설명(주제 상세 상단용). `name`과 동일한 인라인 언어-키 맵에서 사용자 언어 키를 골라(없으면 `en` 폴백) 채운다. 주제 목록 응답에 함께 노출된다 |
| `LifeTipTopic.imageUrl`/`backgroundImageUrl` | string (NOT NULL) | 주제 카드 이미지·주제 상세 상단 배경 이미지(언어 무관 절대 CDN URL). 홈 카드·상세 상단이 항상 이미지를 그리므로 두 필드 모두 필수다(주제엔 "이미지 없는" 경계 케이스가 없다) |
| `LifeTip.imageUrl` | string \| null | 팁 사진(언어 무관). 사진이 없는 팁은 `null`(또는 생략) |

---

## 엔드포인트 요약

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/life-tips/topics` | 생활 팁 주제 전체 목록(이미지·짧은/긴 설명 포함, 노출 순서, 사용자 표시 언어로 번역). 비페이지 | 선택 | 200 |
| GET | `/api/v1/life-tips/topics/{topicCode}/tips` | 해당 주제의 팁 전체(제목·내용·사진, 노출 순서, 사용자 표시 언어로 번역). 비페이지 | 선택 | 200 |

> **인증 = 선택(게스트 허용), 역할 제한 없음**. `/api/v1/life-tips/**`는 `permitAll`이라 `Authorization` 헤더 없이 호출할 수 있고, 헤더가 없으면 **게스트**로 처리한다(issue #181). **`userType=TENANT` 게이트도 제거한다(#181)** — `permitAll`로 비로그인 게스트가 이미 조회할 수 있는 이상 로그인한 임대인만 403으로 막는 것은 앞뒤가 맞지 않고 실효도 없기 때문이다(임대인이 로그아웃하면 그대로 볼 수 있다). 따라서 이 도메인에서 `403 FORBIDDEN`(세입자 아님)은 **더 이상 발생하지 않으며**, 세입자·임대인·게스트 모두 200이다. 표시 언어 선택(`users.lang`)은 선택 필드이며, 미선택이면 저장하지 않고(NULL) 표시 시 `en`으로 폴백한다. 생활 팁은 신원을 저장하지 않으므로(영속에 userId 필드가 없다) 게스트에게 세션 식별자를 요구하지도 발급하지도 않는다.
>
> **註(낡은 게이트 근거 폐기 — #141·#181)**: 이전 판은 "표시 언어 번역이 온보딩(등록 국가)에 의존하므로 ACTIVE 세입자 전용"을 게이트 근거로 삼았다. **이 근거는 이미 두 번 무효화됐다** — (1) #141에서 표시 언어가 등록 국가 도출이 아니라 사용자가 직접 고른 `users.lang` 기반이 되면서 "온보딩 국가 의존"이 사라졌고, (2) #181에서 게스트 표시 언어를 `en` 고정으로 정하면서 로그인 자체에도 의존하지 않게 됐다. 그래서 온보딩 완료도 세입자 역할도 요구하지 않는다 — 온보딩 미완료(`PENDING`/`TERMS_AGREED`, `ROLE_ONBOARDING`) 토큰도 게스트와 동일하게 통과하고, `403 AUTH_ONBOARDING_REQUIRED`·`401 UNAUTHENTICATED`(토큰 누락·위조)·`403 FORBIDDEN`(세입자 아님)은 이 경로에서 모두 **도달 불가**가 된다. 반면 **만료 토큰은 게스트로 강등하지 않고 `401 TOKEN_EXPIRED`** 를 유지한다(토큰을 보냈는데 만료된 것은 게스트가 아니라 재발급이 필요한 회원이다). 구현 시 [`SecurityConfig`](../../../src/main/java/com/kohere/common/security/SecurityConfig.java)의 `/api/v1/life-tips/**` 매처를 `hasRole("USER")`에서 `permitAll()`로 바꾸고, `LifeTipService`의 `assertTenant` 게이트를 제거한다.

### 호출자별 결과

응답 스키마는 호출자와 무관하게 동일하며, 갈리는 것은 **표시 언어**뿐이다.

| 호출자 | 결과 | 표시 언어 |
| --- | --- | --- |
| 비로그인 게스트(토큰 미전송·위조) | 200 | **`en` 고정** — `getLanguage`를 호출하지 않는다 |
| 세입자(`TENANT`, ACTIVE) | 200 | **`users.lang`** — 온보딩에서 고른 표시 언어(선택값이라 미선택이면 `en`) |
| 임대인(`LANDLORD`, ACTIVE) | **200** (종전 403 `FORBIDDEN`에서 변경) | **`ko` 고정** — 임대인 온보딩에서 서버가 `lang='ko'`로 확정하고(`country='KR'`와 함께) 프로필 수정 경로에서도 바뀌지 않으므로, 임대인은 생활 팁을 **한국어로** 본다 |
| 온보딩 미완료(`PENDING`/`TERMS_AGREED`) | 200 | **`en`** — users 행은 있어 `getLanguage`를 호출하지만 `users.lang`이 아직 NULL이라 `en`으로 폴백한다 |
| 만료 토큰 | 401 `TOKEN_EXPIRED` | — |

---

## 상세

### 1. GET `/api/v1/life-tips/topics` — 생활 팁 주제 목록 조회

생활 팁이 어떤 주제로 나뉘어 있는지 **주제 전체 목록**을 노출 순서(`order` 오름차순)대로 반환한다(US-8-1). 주제(`LifeTipTopic`)는 운영이 적재한 큐레이션 카탈로그이며, 각 주제는 언어 무관 식별 `code`(UPPER_SNAKE), 표시명 `name`·짧은 설명 `shortDescription`·긴 설명 `longDescription`(각각 언어-키 맵), 카드 이미지 `imageUrl`·배경 이미지 `backgroundImageUrl`(언어 무관 절대 CDN URL)을 가진다. 홈 화면 주제 카드는 `imageUrl` + `shortDescription`으로, 주제 상세 상단은 `backgroundImageUrl` + `longDescription`으로 그려지며, 앱은 목록에서 받은 주제 객체를 상세 화면까지 그대로 들고 간다(주제는 5건 고정·소규모라 6필드를 한 응답에 실어도 과다 전송 비용이 없다). 서버는 로그인 사용자면 `user`의 `getLanguage(userId)`로 표시 언어를 정하고(게스트는 호출 없이 `en` 고정) 그 언어 키(없으면 `en`)로 `name`·`shortDescription`·`longDescription`을 채우며, 이미지 2필드(`imageUrl`·`backgroundImageUrl`)는 언어와 무관하게 그대로 싣는다. 주제 수는 고정·소규모라 **페이지네이션 없이 전체 배열을 한 번에** 반환한다.

- **인증**: 선택 — `Authorization` 헤더 없이 호출하면 게스트로 처리한다(표시 언어 `en` 고정). **역할 제한은 없다** — 임대인도 200이며 표시 언어는 `ko`다(위 [호출자별 결과](#호출자별-결과)).
- **동작**: `lifeTipTopics` 컬렉션의 전체 주제를 `order` 오름차순으로 정렬해 `{ code, name, shortDescription, longDescription, imageUrl, backgroundImageUrl }` 목록으로 조립한다. `code`는 도큐먼트 `_id`(UPPER_SNAKE)를 그대로 싣고, `name`·`shortDescription`·`longDescription`은 각 도큐먼트의 인라인 언어-키 맵에서 사용자 언어 키를 골라(없으면 `en` 폴백) 채우며, `imageUrl`·`backgroundImageUrl`은 도큐먼트의 절대 CDN URL을 언어와 무관하게 그대로 싣는다(항상 존재 — NOT NULL).
- **번역(US-8-3)**: 표시 텍스트 `name`·`shortDescription`·`longDescription`만 사용자 표시 언어로 번역한다. `code`와 이미지 2필드(`imageUrl`·`backgroundImageUrl`)는 언어와 무관하게 동일하다. 표시 언어는 `Accept-Language` 헤더가 아니라 로그인 사용자면 `user`가 사용자 선택값(`users.lang`)이 있으면 그 값·없으면 `en`으로 정하고, 게스트면 `en` 고정이다(아래 [i18n 절](#i18n번역--adr-0029-재사용)).
- **페이지네이션**: 없음. 고정·소규모 카탈로그라 페이지 객체 없이 전체 배열을 반환한다(api-design-guide §4 비적용, US-7-3과 동일 성격).

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `Authorization` | 선택 | `Bearer <accessToken>`. 없으면 게스트로 처리한다(표시 언어 `en` 고정) |

#### Path / Query 파라미터

없음.

#### Request Body

없음.

#### 성공 Response — 200 OK (공통 래퍼)

표시 텍스트(`name`·`shortDescription`·`longDescription`)는 표시 언어가 일본어(`ja`)인 사용자 예시(미지원 언어면 `en` 폴백). `code`와 이미지 2필드(`imageUrl`·`backgroundImageUrl`)는 번역과 무관하게 동일하다. 페이지 객체 없이 `topics[]` 전체 배열이 노출 순서대로 담긴다.

```jsonc
{
  "success": true,
  "data": {
    "topics": [
      {
        "code": "MOVING_IN",
        "name": "入居・引っ越し",
        "shortDescription": "入居手続きから引っ越しの段取りまで、韓国での新生活スタートに必要な基本情報。",
        "longDescription": "賃貸契約後の入居手続き、公共料金の開通、引っ越し業者の手配、転入に伴う各種届け出まで、韓国で新しい住まいに移る際の流れをまとめました。初めての入居でも迷わないよう、順を追って確認できます。",
        "imageUrl": "https://cdn.kohere.app/life-tips/topics/moving-in/card.png",
        "backgroundImageUrl": "https://cdn.kohere.app/life-tips/topics/moving-in/background.png"
      },
      {
        "code": "ADMINISTRATION",
        "name": "行政手続き",
        "shortDescription": "外国人登録や在留カード関連など、区役所で行う行政手続きの要点。",
        "longDescription": "外国人登録、住所変更、在留資格の更新、印鑑登録など、韓国で暮らすうえで欠かせない行政手続きを解説します。必要書類や窓口、期限を事前に把握して、スムーズに手続きを進めましょう。",
        "imageUrl": "https://cdn.kohere.app/life-tips/topics/administration/card.png",
        "backgroundImageUrl": "https://cdn.kohere.app/life-tips/topics/administration/background.png"
      },
      {
        "code": "TRANSPORT",
        "name": "交通",
        "shortDescription": "地下鉄・バスの乗り方や交通カードなど、韓国の移動に役立つ情報。",
        "longDescription": "T-moneyカードの購入・チャージ方法、地下鉄とバスの乗り換え、タクシーやシェア自転車の利用まで、韓国での日常の移動に必要な交通情報をまとめました。運賃や乗り換え割引の仕組みも紹介します。",
        "imageUrl": "https://cdn.kohere.app/life-tips/topics/transport/card.png",
        "backgroundImageUrl": "https://cdn.kohere.app/life-tips/topics/transport/background.png"
      },
      {
        "code": "FINANCE",
        "name": "銀行・金融",
        "shortDescription": "銀行口座の開設や送金・公共料金の支払いなど、お金まわりの基本。",
        "longDescription": "外国人の銀行口座開設、モバイルバンキングの設定、海外送金、公共料金や家賃の自動振替まで、韓国での金融サービスの使い方を解説します。必要書類や手数料の目安もあわせて確認できます。",
        "imageUrl": "https://cdn.kohere.app/life-tips/topics/finance/card.png",
        "backgroundImageUrl": "https://cdn.kohere.app/life-tips/topics/finance/background.png"
      },
      {
        "code": "HOUSING",
        "name": "住まい",
        "shortDescription": "チョンセ・ウォルセなど韓国独自の賃貸制度と住まい選びのポイント。",
        "longDescription": "チョンセ（전세）とウォルセ（월세）の違い、保証金と管理費の仕組み、契約時の注意点、退去時の精算まで、韓国の住まいに関する制度と実務をまとめました。物件選びで失敗しないための基礎知識が身につきます。",
        "imageUrl": "https://cdn.kohere.app/life-tips/topics/housing/card.png",
        "backgroundImageUrl": "https://cdn.kohere.app/life-tips/topics/housing/background.png"
      }
    ]
  },
  "error": null
}
```

> `topics[]`는 `order` 오름차순으로 정렬된 전체 주제다(페이지네이션 없음). `code`는 언어 무관 불변 식별자(UPPER_SNAKE)이고, 이미지 2필드(`imageUrl`·`backgroundImageUrl`)는 언어 무관 절대 CDN URL로 항상 존재한다(NOT NULL). `name`·`shortDescription`·`longDescription`은 서버가 `lifeTipTopics` 도큐먼트의 각 인라인 언어-키 맵에서 사용자 언어 키를 골라(없으면 `en`) 채운 번역 문자열이다. 미지원 언어면 같은 `code`에 대해 세 텍스트가 영어로 폴백된다(에러 아님). 홈 카드는 `imageUrl`+`shortDescription`, 주제 상세 상단은 `backgroundImageUrl`+`longDescription`으로 그린다. 주제가 0건이면 `topics: []`(에러 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `TOKEN_EXPIRED` | **만료된** access token으로 호출(토큰 미전송·위조는 게스트로 처리되어 200) |

> `permitAll` 전환으로 `401 UNAUTHENTICATED`(토큰 없음·위조)와 `403 AUTH_ONBOARDING_REQUIRED`(온보딩 미완료)는 이 엔드포인트에서 **도달 불가**다 — 전자는 게스트로 처리되고(표시 언어 `en` 고정), 후자는 인가상 게스트와 동일하게 통과하되 표시 언어는 `users.lang`(미설정이면 `en`)을 따른다. **`403 FORBIDDEN`(세입자 아님)도 역할 게이트 제거(#181)로 도달 불가**다 — 로그인한 임대인도 200이다.

---

### 2. GET `/api/v1/life-tips/topics/{topicCode}/tips` — 특정 주제의 생활 팁 목록 조회

고른 주제(`topicCode`)에 속한 생활 팁(**제목 · 내용 · 사진**) 전체를 노출 순서(`order` 오름차순)대로 반환한다(US-8-2). 생활 팁(`LifeTip`)은 하나의 주제(`topicCode`)에 속하며(주제 : 팁 = 1 : N), `title`·`content`는 언어-키 맵으로 임베드되고 `imageUrl`은 언어 무관(사진)이다. 서버는 그 주제의 팁 전체를 조립해 각 팁의 `title`·`content`를 사용자 언어 키(없으면 `en`)로 채우고 `imageUrl`은 그대로 싣는다. 주제당 팁 수가 제한적이므로 **페이지네이션 없이 전체 리스트를 한 번에** 반환한다("해당 주제에 맞는 제목-내용-사진의 모든 리스트").

- **인증**: 선택 — `Authorization` 헤더 없이 호출하면 게스트로 처리한다(표시 언어 `en` 고정). **역할 제한은 없다** — 임대인도 200이며 표시 언어는 `ko`다(위 [호출자별 결과](#호출자별-결과)).
- **동작**: `lifeTipTopics._id == topicCode` 주제의 존재를 확인한 뒤, `lifeTips`에서 `topicCode`가 일치하는 팁을 `order` 오름차순(복합 인덱스 `{ topicCode: 1, order: 1 }`)으로 조회해 `{ id, title, content, imageUrl }` 목록으로 조립한다. `topicCode` 참조는 애플리케이션 레벨 조인이며 DB 조인을 쓰지 않는다(폴리글랏 규약).
- **번역(US-8-3)**: 각 팁의 `title`·`content`만 사용자 표시 언어로 번역한다. `id`(팁 식별자)와 `imageUrl`(사진)은 언어와 무관하게 동일하다. 표시 언어는 `Accept-Language` 헤더가 아니라 로그인 사용자면 `user`가 사용자 선택값(`users.lang`)이 있으면 그 값·없으면 `en`으로 정하고, 게스트면 `en` 고정이다(아래 [i18n 절](#i18n번역--adr-0029-재사용)).
- **사진 없는 팁**: 사진이 없는 팁은 `imageUrl`을 `null`(또는 생략)로 싣고 나머지 필드(`title`·`content`)는 정상 노출한다.
- **존재하지 않는 주제**: 경로의 `{topicCode}`가 `lifeTipTopics`에 없으면 `404 LIFE_TIP_TOPIC_NOT_FOUND`(신규 도메인 에러코드, `*_NOT_FOUND` 규약)를 반환한다.
- **페이지네이션**: 없음. 고정·소규모 카탈로그라 페이지 객체 없이 전체 배열을 반환한다(api-design-guide §4 비적용).

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `Authorization` | 선택 | `Bearer <accessToken>`. 없으면 게스트로 처리한다(표시 언어 `en` 고정) |

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `topicCode` | string | 필수 | 주제 코드(UPPER_SNAKE, `lifeTipTopics._id` 참조). US-8-1 응답의 `topics[].code`를 그대로 사용한다. 카탈로그에 없으면 `404 LIFE_TIP_TOPIC_NOT_FOUND` |

Query 파라미터: 없음.

Request Body: 없음.

#### 성공 Response — 200 OK (공통 래퍼)

제목·내용(`title`·`content`)은 표시 언어가 일본어(`ja`)인 사용자 예시(미지원 언어면 `en` 폴백). `id`·`imageUrl`은 번역과 무관하게 동일하다. 아래는 `GET /api/v1/life-tips/topics/MOVING_IN/tips` 호출 예시로, 팁 3건이 `order` 순으로 담기며 마지막 팁은 사진이 없어 `imageUrl`이 `null`이다.

```jsonc
{
  "success": true,
  "data": {
    "tips": [
      {
        "id": "6858e2000000000000000101",
        "title": "住民登録（外国人登録）の手続き",
        "content": "入居後14日以内に、お住まいの区役所（区庁）で外国人登録を行ってください。パスポート・在留カード・賃貸借契約書が必要です。",
        "imageUrl": "https://cdn.kohere.app/life-tips/6858e2000000000000000101/cover.jpg"
      },
      {
        "id": "6858e2000000000000000102",
        "title": "電気・ガス・水道の開通",
        "content": "公共料金は管理事務所または各供給会社に連絡して名義変更・開通します。管理費に含まれる場合もあるため契約内容を確認してください。",
        "imageUrl": "https://cdn.kohere.app/life-tips/6858e2000000000000000102/cover.jpg"
      },
      {
        "id": "6858e2000000000000000103",
        "title": "ゴミの分別ルール",
        "content": "韓国では従量制ごみ袋（종량제봉투）と食品ごみの分別が必須です。地域ごとに収集日が異なるので掲示を確認してください。",
        "imageUrl": null
      }
    ]
  },
  "error": null
}
```

> `tips[]`는 해당 주제의 팁 전체를 `order` 오름차순으로 담은 배열이다(페이지네이션 없음). `id`(팁 식별자)와 `imageUrl`(사진)은 언어 무관 불변이며, `title`·`content`만 서버가 `lifeTips` 도큐먼트의 인라인 언어-키 맵에서 사용자 언어 키를 골라(없으면 `en`) 채운 번역 문자열이다. 미지원 언어면 같은 `id`에 대해 `title`·`content`가 영어로 폴백된다(에러 아님). 사진이 없는 팁은 `imageUrl`이 `null`(또는 생략)이다. 주제는 존재하나 팁이 0건이면 `tips: []`(에러 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `TOKEN_EXPIRED` | **만료된** access token으로 호출(토큰 미전송·위조는 게스트로 처리되어 200) |
| 404 | `LIFE_TIP_TOPIC_NOT_FOUND` | 경로의 `topicCode`가 카탈로그(`lifeTipTopics`)에 존재하지 않음 |

> `permitAll` 전환으로 `401 UNAUTHENTICATED`(토큰 없음·위조)와 `403 AUTH_ONBOARDING_REQUIRED`(온보딩 미완료)는 이 엔드포인트에서 **도달 불가**다 — 전자는 게스트로 처리되고(표시 언어 `en` 고정), 후자는 인가상 게스트와 동일하게 통과하되 표시 언어는 `users.lang`(미설정이면 `en`)을 따른다. **`403 FORBIDDEN`(세입자 아님)도 역할 게이트 제거(#181)로 도달 불가**다 — 로그인한 임대인도 200이다.

---

## i18n(번역) — ADR-0029 재사용

생활 팁의 번역 전략은 **진단 i18n**([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md), US-2-6)과 **완전히 동일**하며 별도 메커니즘·메시지 컬렉션·번역 키를 만들지 않는다(US-8-3). 진단(02) 스펙의 표시 라벨 번역 방식을 그대로 재사용한다.

- **번역 기준 = 사용자 표시 언어**: 표시 언어는 **로그인 사용자**의 경우 사용자가 고른 표시 언어(`users.lang`)가 있으면 그 값, 없으면 `en`으로 정한다(게스트는 아래 항목대로 `en` 고정). `Accept-Language` 헤더·토큰 클레임은 사용하지 않는다 — 사용자가 `Accept-Language`를 다른 값으로 보내도 응답 언어는 서버가 도출한 표시 언어로 결정된다.
- **게스트는 `en` 고정**: 비로그인 요청은 users 행이 없어 `getLanguage`를 호출할 수 없으므로(호출하면 `404 USER_NOT_FOUND`) **호출 자체를 건너뛰고** `en`으로 정한다. 게스트에게도 `Accept-Language`는 사용하지 않는다 — 지원 언어가 `en`/`ko`/`ja`로 한정돼 임의 로케일 매핑 정책이 별도로 필요하므로 후속 과제로 열어둔다. 온보딩 미완료(`PENDING`/`TERMS_AGREED`) 토큰은 users 행이 존재하므로 게스트가 아니라 **`users.lang`을 따르며**, 그 값이 아직 NULL이라 결과적으로 `en`이 된다.
- **임대인은 `ko` 고정**: 임대인은 온보딩에서 서버가 `lang='ko'`(·`country='KR'`)로 확정하고 임대인 프로필 수정 경로는 표시 언어를 다루지 않으므로, `users.lang`이 항상 `ko`다 — 즉 임대인은 생활 팁을 **한국어로** 본다(#141). 이는 접근 제한이 아니라 표시 언어 도출의 결과일 뿐이며, 임대인의 호출은 세입자와 동일하게 200이다(#181).
- **표시 언어 취득**: 로그인 사용자에 한해 서버가 `user` 모듈의 **공개 query `getLanguage(userId)`를 동기 호출**해 표시 언어를 취득한다. `user`가 **사용자가 고른 표시 언어(`users.lang`)이 있으면 그 값, 없으면 `en`**을 반환한다([ADR-0029](../../adr/0029-diagnosis-i18n-strategy.md) 개정(#141); [ADR-0002](../../adr/0002-inter-module-communication-via-events.md) Decision 5 — 모듈 의존 `lifetip → user` 추가, 진단 `diagnosis → user`와 동일 근거).
- **인라인 언어-키 맵 임베드**: 표시 문자열은 별도 메시지 컬렉션 없이 주제·팁 도큐먼트 안 **인라인 언어-키 맵**(`{ "en": …, "ja": …, "ko": … }`)으로 임베드한다 — 주제는 `name`·`shortDescription`·`longDescription`, 팁은 `title`·`content` 각각 언어-키 맵이다(진단 문항 `question`·옵션 `label`과 동일 방식).
- **`en` 폴백**: 서버는 사용자 언어 키로 문자열을 고르고, 그 키가 없으면 **영어(`en`)로 폴백**한다(에러 아님).
- **식별자·이미지·사진 불변**: 주제·팁 식별자(`code`/`id`), 주제 이미지(`LifeTipTopic.imageUrl`·`backgroundImageUrl`), 팁 사진(`LifeTip.imageUrl`)은 언어 무관 불변이고 표시 텍스트(주제 `name`·`shortDescription`·`longDescription`, 팁 `title`·`content`)만 언어별이다. **응답 스키마는 언어와 무관하게 동일**하며 서버가 언어 문자열만 채운다.

---

## 도메인 에러 코드

> 공통 코드(`TOKEN_EXPIRED`, `INTERNAL_ERROR` 등)는 [error-response-guide](../error-response-guide.md) §4의 정의를 그대로 쓰며 여기서 재정의하지 않는다. 5xx(`INTERNAL_ERROR` 등)는 전 엔드포인트에 공통 적용되므로 개별 표에 반복 기재하지 않는다. 아래는 본 도메인 고유 코드만 정의한다(prefix `LIFE_TIP`).

| code | status | 의미 |
| --- | --- | --- |
| `LIFE_TIP_TOPIC_NOT_FOUND` | 404 | 경로의 `topicCode`가 카탈로그(`lifeTipTopics`)에 존재하지 않음(`*_NOT_FOUND` 규약, `ErrorCode` 신규 등록 필요) |

> 만료 토큰만 공통 `TOKEN_EXPIRED`(401)를 그대로 사용한다. 게이트 해제(`permitAll` 전환 + 역할 게이트 제거, #181)로 `UNAUTHENTICATED`(401, 토큰 없음·위조)·`AUTH_ONBOARDING_REQUIRED`(403, 온보딩 미완료)·`FORBIDDEN`(403, 세입자 아님)은 본 도메인에서 발생하지 않는다. 생활 팁 도메인은 읽기 전용이라 입력 검증(`INVALID_INPUT`)·본문 파싱(`MALFORMED_REQUEST`)이 발생하지 않으며, 주제 미존재만 도메인 고유 코드로 둔다.
