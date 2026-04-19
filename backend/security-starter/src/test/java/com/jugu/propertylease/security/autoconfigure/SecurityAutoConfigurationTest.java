package com.jugu.propertylease.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.autoconfigure.servlet.MockServletSecurityAutoConfiguration;
import com.jugu.propertylease.security.autoconfigure.servlet.ServletSecurityAutoConfiguration;
import com.jugu.propertylease.security.filter.servlet.MockUserFilter;
import com.jugu.propertylease.security.filter.servlet.ServletServiceJwtFilter;
import com.jugu.propertylease.security.interceptor.ServiceTokenClientInterceptor;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Integration-style tests for AutoConfiguration using ApplicationContextRunner. No embedded server
 * is started.
 */
class SecurityAutoConfigurationTest {

  private static final String SERVICE_PROPS =
      "security.mode=service," +
          "security.service-name=billing-service," +
          "security.jwt.service.secret=service-secret-at-least-32-bytes!!," +
          "security.jwt.service.expiration=300";
  private static final String MOCK_PROPS =
      "security.mode=mock," +
          "security.service-name=billing-service";
  private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          JacksonAutoConfiguration.class,
          SecurityAutoConfiguration.class,
          SecurityFilterAutoConfiguration.class,
          SecurityStarterAutoConfiguration.class,
          ServletSecurityAutoConfiguration.class,
          MockServletSecurityAutoConfiguration.class));

  // ===== service 模式 =====

  @Test
  void serviceModeContext_registersSecurityFilterChain() {
    contextRunner
        .withPropertyValues(SERVICE_PROPS.split(","))
        .run(ctx -> assertThat(ctx).hasSingleBean(SecurityFilterChain.class));
  }

  @Test
  void serviceModeContext_registersCommonBeans() {
    contextRunner
        .withPropertyValues(SERVICE_PROPS.split(","))
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(JwtTokenParser.class);
          assertThat(ctx).hasSingleBean(ServiceTokenGenerator.class);
          assertThat(ctx).hasSingleBean(ServiceTokenClientInterceptor.class);
          assertThat(ctx).hasSingleBean(ServletServiceJwtFilter.class);
        });
  }

  @Test
  void serviceModeContext_registersServiceJwtValidator() {
    contextRunner
        .withPropertyValues(SERVICE_PROPS.split(","))
        .run(ctx -> assertThat(ctx).hasSingleBean(ServiceJwtValidator.class));
  }

  @Test
  void serviceModeContext_doesNotRegisterUserJwtValidator() {
    contextRunner
        .withPropertyValues(SERVICE_PROPS.split(","))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(UserJwtValidator.class));
  }

  @Test
  void serviceModeContext_doesNotRegisterMockUserFilter() {
    contextRunner
        .withPropertyValues(SERVICE_PROPS.split(","))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MockUserFilter.class));
  }

  // ===== gateway 模式 =====

  @Test
  void gatewayModeContext_registersUserJwtValidator() {
    contextRunner
        .withPropertyValues(
            "security.mode=gateway",
            "security.service-name=gateway",
            "security.jwt.user.secret=user-secret-at-least-32-bytes!!",
            "security.jwt.user.expiration=1800",
            "security.jwt.service.secret=service-secret-at-least-32-bytes!!",
            "security.jwt.service.expiration=300")
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(UserJwtValidator.class);
          assertThat(ctx).hasSingleBean(ServiceJwtValidator.class);
        });
  }

  @Test
  void gatewayModeWithoutUserSecret_contextFails() {
    contextRunner
        .withPropertyValues(
            "security.mode=gateway",
            "security.service-name=gateway",
            "security.jwt.service.secret=service-secret-at-least-32-bytes!!",
            "security.jwt.service.expiration=300")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  // ===== 缺失配置快速失败 =====

  @Test
  void missingServiceName_contextFails() {
    contextRunner
        .withPropertyValues(
            "security.mode=service",
            "security.jwt.service.secret=service-secret-at-least-32-bytes!!",
            "security.jwt.service.expiration=300")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void missingServiceSecret_inServiceMode_contextFails() {
    contextRunner
        .withPropertyValues(
            "security.mode=service",
            "security.service-name=billing-service")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  // ===== mock 模式 =====

  @Test
  void mockModeContext_registersSecurityFilterChain() {
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> assertThat(ctx).hasSingleBean(SecurityFilterChain.class));
  }

  @Test
  void mockModeContext_registersMockUserFilter() {
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> assertThat(ctx).hasSingleBean(MockUserFilter.class));
  }

  @Test
  void mockModeContext_registersCommonBeans() {
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(JwtTokenParser.class);
          assertThat(ctx).hasSingleBean(ServiceTokenGenerator.class);
          assertThat(ctx).hasSingleBean(ServiceTokenClientInterceptor.class);
        });
  }

  @Test
  void mockModeContext_doesNotRequireJwtConfig() {
    // mock 模式不配置任何 JWT secret，Context 应正常启动
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void mockModeContext_doesNotRegisterServletServiceJwtFilter() {
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ServletServiceJwtFilter.class));
  }

  @Test
  void mockModeContext_doesNotRegisterAnyJwtValidator() {
    contextRunner
        .withPropertyValues(MOCK_PROPS.split(","))
        .run(ctx -> {
          assertThat(ctx).doesNotHaveBean(UserJwtValidator.class);
          assertThat(ctx).doesNotHaveBean(ServiceJwtValidator.class);
        });
  }

  @Test
  void mockModeContext_customMockUser_configIsApplied() {
    contextRunner
        .withPropertyValues(
            "security.mode=mock",
            "security.service-name=billing-service",
            "security.mock-user.user-id=99",
            "security.mock-user.permissions[0]=lease:read",
            "security.mock-user.permissions[1]=lease:write")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).hasSingleBean(MockUserFilter.class);
        });
  }
}
