package com.kohere.auth.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * dev·local 전용 고정 인증번호 설정(app.auth.fixed-verification, 이슈 #180). 앱스토어 심사자가 실제 SMS·이메일을 수신할 수 없어,
 * 지정된 심사 계정에 한해 인증번호를 고정값으로 발급하고 외부 발송을 생략한다. <b>운영에서는 절대 활성화하지 않는다</b>.
 *
 * <p>이중 게이트로 보호한다 — {@link FixedVerificationPolicy}는 {@code @Profile({"local","dev"})} + {@code
 * enabled=true}일 때만 빈으로 등록된다(prod 프로파일엔 존재조차 하지 않음, {@link TestLoginProperties}와 동일 방식).
 *
 * <p><b>역할을 설정에서 명시한다.</b> 시스템의 {@code userType}은 온보딩 시점에야 확정되므로(그 전에는 전원 기본 TENANT) 인증 단계에서는 역할을 알
 * 수 없다. 대신 심사 계정은 우리가 직접 등록하는 값이므로, 임차인·임대인 그룹을 나눠 적어 <b>채널을 역할에 가둔다</b> — 임차인 계정의 SMS 인증, 임대인 계정의
 * 이메일 인증은 실제 발송으로 흘려보내지 않고 거절한다.
 *
 * <p>그룹 안에서는 계정과 값을 묶지 않는다. 심사자는 어느 계정으로 로그인했는지와 무관하게 노트에 적힌 값을 입력하므로, 계정별로 값을 묶으면 같은 값이 로그인 계정에 따라
 * 되기도 안 되기도 한다. 그룹 단위 허용목록이면 그 혼선 없이 폭발 반경은 등록된 값으로 한정된다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.fixed-verification")
public class FixedVerificationProperties {

  /** 고정 인증번호 활성화(미설정 시 비활성). true여도 {@code @Profile}가 local·dev로 한정한다. */
  private boolean enabled = false;

  /** 발급할 고정 인증번호(숫자만). 비어 있으면 기동 시 실패한다. */
  private String code;

  /** 임차인 트랙 심사 설정 — 이메일 인증만 허용한다. */
  private Tenant tenant = new Tenant();

  /** 임대인 트랙 심사 설정 — SMS 인증만 허용한다. */
  private Landlord landlord = new Landlord();

  /** 임차인 심사 그룹. 로그인 계정 목록과, 그 계정들이 고정 인증번호로 인증할 수 있는 이메일 목록. */
  @Getter
  @Setter
  public static class Tenant {

    /** 임차인 심사자가 소셜 로그인에 쓰는 실제 Google 계정 이메일 목록. */
    private List<String> googleEmails = new ArrayList<>();

    /** 고정 인증번호가 적용되는 인증 대상 이메일 목록. */
    private List<String> emails = new ArrayList<>();
  }

  /** 임대인 심사 그룹. 로그인 계정 목록과, 그 계정들이 고정 인증번호로 인증할 수 있는 휴대폰 번호 목록. */
  @Getter
  @Setter
  public static class Landlord {

    /** 임대인 심사자가 소셜 로그인에 쓰는 실제 Google 계정 이메일 목록. */
    private List<String> googleEmails = new ArrayList<>();

    /** 고정 인증번호가 적용되는 인증 대상 휴대폰 번호 목록. */
    private List<String> phoneNumbers = new ArrayList<>();
  }

  /** 이메일 비교 정규화 — 공백 제거 + 소문자. 값이 비면 null(어떤 값과도 일치하지 않음). */
  static String normalizeEmail(String email) {
    return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
  }

  /** 전화번호 비교 정규화 — 숫자만 남긴다(하이픈·공백 표기 차이 흡수). 숫자가 없으면 null. */
  static String normalizePhoneNumber(String phoneNumber) {
    if (!StringUtils.hasText(phoneNumber)) {
      return null;
    }
    String digits = phoneNumber.replaceAll("\\D", "");
    return digits.isEmpty() ? null : digits;
  }

  /** 빈 항목을 걸러 정규화한 이메일 집합. 환경변수 미주입 시 빈 문자열이 섞여 들어오므로 반드시 거른다. */
  static List<String> normalizedEmails(List<String> raw) {
    return raw.stream()
        .map(FixedVerificationProperties::normalizeEmail)
        .filter(v -> v != null)
        .toList();
  }

  /** 빈 항목을 걸러 정규화한 전화번호 집합. */
  static List<String> normalizedPhoneNumbers(List<String> raw) {
    return raw.stream()
        .map(FixedVerificationProperties::normalizePhoneNumber)
        .filter(v -> v != null)
        .toList();
  }
}
