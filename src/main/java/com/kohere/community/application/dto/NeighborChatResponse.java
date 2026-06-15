package com.kohere.community.application.dto;

/**
 * 동네친구 1:1 채팅 시작 결과. 04(채팅) 스펙의 {@code NEIGHBOR} 카테고리 채팅방 정보를 담는다.
 *
 * <p>docs/api/specs/05-community.md 동네친구 채팅 시작. {@code created}가 {@code false}면 기존 방을 반환한 것이며
 * status는 200, {@code true}면 신규 생성으로 201이다. {@code category}는 {@code "NEIGHBOR"} 문자열을 담는다(chat 모듈
 * enum을 직접 import 하지 않는다).
 */
public record NeighborChatResponse(
    Long chatRoomId, String category, Long peerId, boolean created) {}
