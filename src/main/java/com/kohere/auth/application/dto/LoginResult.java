package com.kohere.auth.application.dto;

/**
 * 웹 로그인 유스케이스의 내부 반환값 — <b>응답 본문({@link LoginResponse})과 쿠키로 나갈 refresh 원문을 분리</b>해 담는다({@link
 * SignupResult}와 같은 운반체).
 *
 * <p>{@link LoginResponse}에 {@code refreshToken}을 넣으면 "쿠키로만 내린다"는 결정(ADR-0048)이 본문 노출로 무너지고, 반대로 응용
 * 계층이 {@code HttpServletResponse}를 받아 직접 쿠키를 내리면 서블릿 타입이 유스케이스로 새어 들어간다. 그래서 <b>응용 계층은 토큰 문자열까지만
 * 돌려주고 쿠키 조립·전송은 프레젠테이션 계층</b>이 맡는다. 이 레코드는 그 경계를 건너는 운반체일 뿐 응답으로 직렬화되지 않는다.
 *
 * <p>가입({@link SignupResult})과 형태가 같아 하나로 합치고 싶어지지만, 그러려면 응답 타입을 제네릭으로 열어야 하고 그 순간 "이 자리에 오는 응답에는
 * refresh가 없다"는 계약이 타입에서 사라진다 — 레코드 하나가 두 벌인 편이 싸다.
 */
public record LoginResult(LoginResponse response, String refreshToken) {}
