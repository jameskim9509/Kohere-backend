package com.kohere.common.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 접근 로그 인터셉터 등록(ADR-0038 용도 1).
 *
 * <p>제외 경로는 신원이 무관하고 볼륨만 만드는 것들이다 — 헬스체크는 ALB가 주기적으로 때리고, 문서·Swagger 자산은 정적 파일이다. {@code
 * PublicPaths}(보안 공개 티어)와 셋이 겹치지만 별개 개념이라, social-login·reissue는 공개 티어여도 용도 3이 필요로 하므로 접근 로그에
 * <b>남긴다</b>.
 */
@Configuration
public class LoggingWebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new AccessLogInterceptor())
        .excludePathPatterns("/actuator/health", "/swagger-ui/**");
  }
}
