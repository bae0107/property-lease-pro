package com.jugu.propertylease.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityPermissionEvaluatorTest {

  private SecurityPermissionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator = new SecurityPermissionEvaluator();
  }

  @Test
  void hasPermission_withMatchingAuthority_returnsTrue() {
    var token = ServiceJwtAuthenticationToken.ofUser(1L, "svc",
        List.of("order:read", "order:write"));

    assertThat(evaluator.hasPermission(token, null, "order:read")).isTrue();
  }

  @Test
  void hasPermission_withNonMatchingAuthority_returnsFalse() {
    var token = ServiceJwtAuthenticationToken.ofUser(1L, "svc", List.of("order:read"));

    assertThat(evaluator.hasPermission(token, null, "order:write")).isFalse();
  }

  @Test
  void hasPermission_systemCall_returnsFalse() {
    var token = ServiceJwtAuthenticationToken.ofSystem("billing-service");

    assertThat(evaluator.hasPermission(token, null, "order:read")).isFalse();
  }

  @Test
  void hasPermission_nullAuthentication_returnsFalse() {
    assertThat(evaluator.hasPermission(null, null, "order:read")).isFalse();
  }

  @Test
  void hasPermission_nullPermission_returnsFalse() {
    var token = ServiceJwtAuthenticationToken.ofUser(1L, "svc", List.of("order:read"));

    assertThat(evaluator.hasPermission(token, null, null)).isFalse();
  }

  @Test
  void hasPermission_withTargetIdAndType_delegatesToPermissionString() {
    var token = ServiceJwtAuthenticationToken.ofUser(1L, "svc", List.of("order:read"));

    // should delegate to first hasPermission overload
    assertThat(evaluator.hasPermission(token, 100L, "ORDER", "order:read")).isTrue();
    assertThat(evaluator.hasPermission(token, 100L, "ORDER", "order:write")).isFalse();
  }

  @Test
  void hasPermission_nonServiceJwtAuthentication_returnsFalse() {
    // plain UsernamePasswordAuthenticationToken is not ServiceJwtAuthenticationToken
    var other = new org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken("user", "pass");

    assertThat(evaluator.hasPermission(other, null, "order:read")).isFalse();
  }
}
