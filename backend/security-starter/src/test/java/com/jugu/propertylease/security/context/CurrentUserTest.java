package com.jugu.propertylease.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // ─── authenticated user ───

  @Test
  void getCurrentUserId_withAuthenticatedUser_returnsUserId() {
    setUserToken(99L, "gateway", List.of("order:read"));

    assertThat(CurrentUser.getCurrentUserId()).isEqualTo(99L);
  }

  @Test
  void getCallerName_withAuthenticatedUser_returnsCallerName() {
    setUserToken(1L, "gateway", List.of());

    assertThat(CurrentUser.getCallerName()).isEqualTo("gateway");
  }

  @Test
  void getPermissions_withAuthenticatedUser_returnsPermissions() {
    setUserToken(1L, "svc", List.of("order:read", "device:command"));

    assertThat(CurrentUser.getPermissions())
        .containsExactlyInAnyOrder("order:read", "device:command");
  }

  @Test
  void isAuthenticated_withToken_returnsTrue() {
    setUserToken(1L, "svc", List.of());
    assertThat(CurrentUser.isAuthenticated()).isTrue();
  }

  @Test
  void isSystemCall_withUserToken_returnsFalse() {
    setUserToken(1L, "svc", List.of());
    assertThat(CurrentUser.isSystemCall()).isFalse();
  }

  // ─── system call ───

  @Test
  void isSystemCall_withSystemToken_returnsTrue() {
    setSystemToken("billing-service");
    assertThat(CurrentUser.isSystemCall()).isTrue();
  }

  @Test
  void getCurrentUserId_withSystemToken_returnsNull() {
    setSystemToken("billing-service");
    assertThat(CurrentUser.getCurrentUserId()).isNull();
  }

  @Test
  void getPermissions_withSystemToken_returnsEmptySet() {
    setSystemToken("billing-service");
    assertThat(CurrentUser.getPermissions()).isEmpty();
  }

  // ─── unauthenticated ───

  @Test
  void getCurrentUserId_unauthenticated_returnsNull() {
    assertThat(CurrentUser.getCurrentUserId()).isNull();
  }

  @Test
  void getCallerName_unauthenticated_returnsNull() {
    assertThat(CurrentUser.getCallerName()).isNull();
  }

  @Test
  void getPermissions_unauthenticated_returnsEmptySet() {
    assertThat(CurrentUser.getPermissions()).isEmpty();
  }

  @Test
  void isAuthenticated_unauthenticated_returnsFalse() {
    assertThat(CurrentUser.isAuthenticated()).isFalse();
  }

  @Test
  void isSystemCall_unauthenticated_returnsFalse() {
    assertThat(CurrentUser.isSystemCall()).isFalse();
  }

  @Test
  void getAuthentication_withToken_returnsPresent() {
    setUserToken(1L, "svc", List.of());
    Optional<ServiceJwtAuthenticationToken> opt = CurrentUser.getAuthentication();
    assertThat(opt).isPresent();
    assertThat(opt.get().getUserId()).isEqualTo(1L);
  }

  @Test
  void getAuthentication_unauthenticated_returnsEmpty() {
    assertThat(CurrentUser.getAuthentication()).isEmpty();
  }

  // ─── helpers ───

  private void setUserToken(Long userId, String callerName, List<String> perms) {
    var token = ServiceJwtAuthenticationToken.ofUser(userId, callerName, perms);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  private void setSystemToken(String serviceName) {
    var token = ServiceJwtAuthenticationToken.ofSystem(serviceName);
    SecurityContextHolder.getContext().setAuthentication(token);
  }
}
