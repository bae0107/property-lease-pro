package com.jugu.propertylease.security.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jugu.propertylease.security.constants.SecurityConstants;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

  private SecurityProperties props;

  @BeforeEach
  void setUp() {
    props = new SecurityProperties();

    SecurityProperties.JwtConfig jwt = new SecurityProperties.JwtConfig();
    JwtProperties service = new JwtProperties();
    service.setSecret("service-secret-at-least-32-bytes!!");
    service.setExpiration(300);
    jwt.setService(service);
    props.setJwt(jwt);

    props.setMode("service");
    props.setServiceName("billing-service");
  }

  @Test
  void getEffectivePermitPaths_withNoCustomPaths_containsOnlyDefaults() {
    List<String> paths = props.getEffectivePermitPaths();
    assertThat(paths).containsExactlyElementsOf(SecurityConstants.DEFAULT_PERMIT_PATHS);
  }

  @Test
  void getEffectivePermitPaths_withCustomPaths_mergesWithDefaults() {
    props.setPermitPaths(List.of("/api/v1/auth/login", "/api/v1/auth/refresh"));

    List<String> paths = props.getEffectivePermitPaths();

    assertThat(paths).contains("/actuator/**");
    assertThat(paths).contains("/error");
    assertThat(paths).contains("/api/v1/auth/login");
    assertThat(paths).contains("/api/v1/auth/refresh");
    assertThat(paths).hasSize(4);
  }

  @Test
  void getEffectivePermitPaths_returnsImmutableList() {
    List<String> paths = props.getEffectivePermitPaths();
    assertThatThrownBy(() -> paths.add("/hack"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getEffectivePermitPaths_doesNotModifyOriginalDefaultList() {
    props.setPermitPaths(List.of("/api/v1/auth/login"));
    props.getEffectivePermitPaths(); // 调用一次

    // DEFAULT_PERMIT_PATHS 不应被修改
    assertThat(SecurityConstants.DEFAULT_PERMIT_PATHS).containsExactly("/actuator/**", "/error");
  }

  @Test
  void getEffectivePermitPaths_withNullPermitPaths_onlyReturnsDefaults() {
    props.setPermitPaths(null);
    List<String> paths = props.getEffectivePermitPaths();
    assertThat(paths).containsExactlyElementsOf(SecurityConstants.DEFAULT_PERMIT_PATHS);
  }

  @Test
  void setPermitPaths_withNull_defaultsToEmptyList() {
    props.setPermitPaths(null);
    assertThat(props.getPermitPaths()).isNotNull().isEmpty();
  }

  @Test
  void jwtConfig_serviceSecretIsReadable() {
    assertThat(props.getJwt().getService().getSecret())
        .isEqualTo("service-secret-at-least-32-bytes!!");
  }

  @Test
  void jwtConfig_userSecretIsNullableForServiceMode() {
    // mode=service 时 jwt.user 可以完全省略
    assertThat(props.getJwt().getUser()).isNull();
  }
}
