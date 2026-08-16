package com.kohere.auth.application.dto;

/**
 * 웹 회원가입 유스케이스의 내부 반환값 — <b>응답 본문({@link SignupResponse})과 쿠키로 나갈 refresh 원문을 분리</b>해 담는다.
 *
 * <p>둘을 한 DTO에 합치지 않는 이유는 명확하다 — {@link SignupResponse}는 그대로 직렬화돼 나가는 응답 계약이라 거기에 {@code
 * refreshToken}을 두면 "쿠키로만 내린다"는 결정(ADR-0048)이 본문 노출로 무너진다. 그렇다고 응용 계층이 {@code HttpServletResponse}를
 * 받아 쿠키를 직접 내리면 서블릿 타입이 유스케이스로 새어 들어간다.
 *
 * <p>그래서 <b>응용 계층은 토큰 문자열까지만 돌려주고, 쿠키 조립·전송은 프레젠테이션 계층</b>이 맡는다(가입용 SMS 인증에서 IP 추출을 컨트롤러에만 둔 것과 같은
 * 분업). 이 레코드는 그 경계를 건너는 운반체일 뿐 응답으로 직렬화되지 않는다.
 */
public record SignupResult(SignupResponse response, String refreshToken) {}
