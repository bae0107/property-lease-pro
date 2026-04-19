package com.jugu.propertylease.security.datapermission;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class DataPermissionInterceptorTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    DataPermissionContext.clear();
  }

  @Test
  void preHandle_setsUserIdFromSecurityContext() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofUser(77L, "svc", List.of()));

    DataPermissionInterceptor interceptor = new DataPermissionInterceptor();
    boolean result = interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(DataPermissionContext.get()).isEqualTo(77L);
  }

  @Test
  void preHandle_systemCall_setsNull() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofSystem("billing-service"));

    DataPermissionInterceptor interceptor = new DataPermissionInterceptor();
    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(DataPermissionContext.get()).isNull();
  }

  @Test
  void preHandle_unauthenticated_setsNull() throws Exception {
    DataPermissionInterceptor interceptor = new DataPermissionInterceptor();
    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(DataPermissionContext.get()).isNull();
  }

  @Test
  void afterCompletion_clearsContext_evenOnException() throws Exception {
    DataPermissionContext.set(99L);
    DataPermissionInterceptor interceptor = new DataPermissionInterceptor();

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(),
        new Object(), new RuntimeException("test error"));

    assertThat(DataPermissionContext.get()).isNull();
  }

  @Test
  void afterCompletion_clearsContext_withNullException() throws Exception {
    DataPermissionContext.set(99L);
    DataPermissionInterceptor interceptor = new DataPermissionInterceptor();

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(),
        new Object(), null);

    assertThat(DataPermissionContext.get()).isNull();
  }
}
