package com.kohere.auth.presentation.dto;

import com.kohere.common.request.Emails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 임대인 웹 로그인 요청 DTO(POST /api/v1/auth/login, 비로그인). 웹 로그인 ID는 {@code local_accounts.email}이므로 계정을
 * 특정하는 값은 {@code email} 하나다(US-1-12).
 *
 * <p><b>비밀번호에 {@code @Pattern}(가입 정책)을 걸지 않는다.</b> {@link SignupRequest#PASSWORD_PATTERN}은 <b>생성
 * 시점</b>의 규칙이고, 이미 만들어진 비밀번호를 로그인에서 다시 심사하면 정책을 한 글자만 조여도 <b>그 전에 가입한 사람들이 전부 400으로 잠긴다</b> — 자기
 * 비밀번호는 맞는데 서버가 대조조차 하지 않는 상태라 사용자가 할 수 있는 일이 없다(해제 경로도 없다). 형식이 아무리 이상해도 저장된 해시와 맞지 않으면 어차피 401이므로
 * 정책 검사가 막아 주는 것도 없다.
 *
 * <p><b>이메일에 길이 상한을 두지 않는 것도 같은 이유다.</b> {@code SignupRequest}가 {@code @Size(max = 255)}를 갖는 것은 그
 * 값을 {@code VARCHAR(255)} 컬럼에 <b>저장</b>하기 때문이고(상한이 없으면 INSERT에서 500), 로그인은 저장하지 않는다. 여기에 상한을 두면
 * 길이만으로 400과 401이 갈려 "형식은 맞지만 등록되지 않은 주소는 401"이라는 계약(스펙 §1-4)에 예외가 하나 생긴다.
 *
 * <p>{@code @NotBlank}는 <b>대조하기 전에</b> 빈 값을 걸러 낸다(400 {@code INVALID_INPUT} + {@code errors[]}) — 빈
 * 비밀번호를 BCrypt까지 흘려보내면 자격증명 오류와 입력 오류가 같은 401로 뭉개져 클라이언트가 "다시 입력하세요"조차 안내하지 못한다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1-4 · ADR-0047.
 */
public record LoginRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = Emails.PATTERN) String email,
    @NotBlank String password) {}
