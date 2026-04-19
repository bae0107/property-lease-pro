package com.jugu.propertylease.security.constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConstantsTest {

  @Test
  void defaultPermitPaths_containsActuatorAndError() {
    assertThat(SecurityConstants.DEFAULT_PERMIT_PATHS)
        .containsExactlyInAnyOrder("/actuator/**", "/error");
  }

  @Test
  void defaultPermitPaths_isImmutable() {
    assertThatThrownBy(() -> SecurityConstants.DEFAULT_PERMIT_PATHS.add("/hacked"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void headerNames_areCorrect() {
    assertThat(SecurityConstants.HEADER_SERVICE_TOKEN).isEqualTo("X-Service-Token");
    assertThat(SecurityConstants.HEADER_TRACE_ID).isEqualTo("X-Trace-Id");
  }

  @Test
  void claimKeys_areCorrect() {
    assertThat(SecurityConstants.CLAIM_USER_ID).isEqualTo("userId");
    assertThat(SecurityConstants.CLAIM_PERMISSIONS).isEqualTo("permissions");
  }
}
