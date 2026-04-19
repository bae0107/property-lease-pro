package com.jugu.propertylease.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ServiceJwtAuthenticationTokenTest {

  @Test
  void ofUser_setsAllFieldsCorrectly() {
    ServiceJwtAuthenticationToken token = ServiceJwtAuthenticationToken
        .ofUser(42L, "gateway", List.of("order:read", "order:write"));

    assertThat(token.getUserId()).isEqualTo(42L);
    assertThat(token.getCallerName()).isEqualTo("gateway");
    assertThat(token.isAuthenticated()).isTrue();
    assertThat(token.getCredentials()).isNull();
    assertThat(token.getPrincipal()).isEqualTo("gateway");
    assertThat(token.getAuthorities())
        .containsExactlyInAnyOrder(
            new SimpleGrantedAuthority("order:read"),
            new SimpleGrantedAuthority("order:write"));
  }

  @Test
  void ofSystem_hasNullUserIdAndEmptyAuthorities() {
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofSystem("billing-service");

    assertThat(token.getUserId()).isNull();
    assertThat(token.getCallerName()).isEqualTo("billing-service");
    assertThat(token.isAuthenticated()).isTrue();
    assertThat(token.getAuthorities()).isEmpty();
  }

  @Test
  void ofUser_withEmptyPermissions_hasNoAuthorities() {
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofUser(1L, "svc", List.of());

    assertThat(token.getAuthorities()).isEmpty();
  }

  @Test
  void ofUser_withNullPermissions_hasNoAuthorities() {
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofUser(1L, "svc", null);

    assertThat(token.getAuthorities()).isEmpty();
  }

  @Test
  void setAuthenticated_true_throwsException() {
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofSystem("svc");

    assertThatThrownBy(() -> token.setAuthenticated(true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setAuthenticated_false_isAllowed() {
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofSystem("svc");

    assertThatCode(() -> token.setAuthenticated(false)).doesNotThrowAnyException();
    assertThat(token.isAuthenticated()).isFalse();
  }

  @Test
  void ofUser_permissionsPreserveInsertionOrder() {
    List<String> perms = List.of("a", "b", "c");
    ServiceJwtAuthenticationToken token =
        ServiceJwtAuthenticationToken.ofUser(1L, "svc", perms);

    var authorities = token.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .toList();
    assertThat(authorities).containsExactly("a", "b", "c");
  }
}
