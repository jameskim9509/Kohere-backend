package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시 어댑터 — Spring Security BCrypt(기본 강도 10, salt는 해시 문자열 안에 포함되어 별도 컬럼이 없다). 저장 형식은 60자이고 컬럼은
 * 여유를 둔 {@code VARCHAR(100)}이다(V22).
 *
 * <p><b>{@link PasswordEncoder}를 빈으로 공개하지 않고 이 어댑터가 직접 생성한다.</b> 이 저장소에는 {@code
 * AuthenticationManager}·{@code AuthenticationProvider}·{@code UserDetailsService}·{@code
 * JwtDecoder} 빈이 하나도 없어 Boot의 {@code UserDetailsServiceAutoConfiguration}이 그대로 활성이고, 그 자동 설정은
 * {@code PasswordEncoder} 빈이 컨텍스트에 있으면 기본 사용자의 생성 비밀번호에 {@code {noop}} 접두사를 붙이지 않는다. 즉 전역 빈을 하나 올리는
 * 것만으로 우리가 쓰지도 않는 자동 설정의 동작이 조용히 바뀐다. 해시가 필요한 곳은 웹 자격증명 한 군데뿐이라(폼로그인·httpBasic은 {@code
 * SecurityConfig}에서 비활성) 전역으로 노출할 이유가 없다.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  /** 저장 해시가 비었거나 BCrypt 형식이 아니면 예외가 아니라 {@code false}다(불일치와 같게 다룬다). */
  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return encoder.matches(rawPassword, passwordHash);
  }
}
