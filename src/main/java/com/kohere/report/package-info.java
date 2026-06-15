/**
 * 콘텐츠 신고 Bounded Context. 게시글(POST)·댓글(COMMENT)·채팅 메시지(MESSAGE)에 대한 신고를 접수·저장하고, 신고 사유 enum 메타를
 * 노출한다. MVP 범위는 신고 접수/저장 및 사유 메타 조회까지이며, 운영자 검토·제재 흐름은 포함하지 않는다.
 *
 * <p>도메인 에러 코드 prefix: {@code REPORT}. 스펙: docs/api/specs/07-reports.md.
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다.
 *
 * <p>TODO: 신고 대상 존재검증·자기 신고 차단은 다른 모듈(listing/community/chat) 정보가 필요하므로, 공개 API·이벤트로 연결한다(현재는 직접
 * import 금지 — 모듈 경계 유지).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Report",
    allowedDependencies = {"common"})
package com.kohere.report;
