package com.kohere.user.domain;

/**
 * 국가 참조 값. 국적은 {@code code}(ISO 3166-1 alpha-2)로 식별하고, 표시명({@code name})·국기({@code flag})는 {@code
 * countries} 참조 데이터에서 확보한다(클라이언트는 국가 코드만 전송). {@code flag}는 국기 이미지 URL(flagcdn.com SVG)이다.
 * database-design §4-2 · domain-model §2(국가 참조).
 */
public record Country(String code, String name, String flag) {}
