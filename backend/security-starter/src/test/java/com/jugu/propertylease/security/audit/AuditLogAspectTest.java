package com.jugu.propertylease.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLogAspectTest {

  private AuditLogger mockLogger;
  private SampleService proxy;

  @BeforeEach
  void setUp() {
    mockLogger = mock(AuditLogger.class);
    // Wire up AOP proxy manually (no Spring context needed)
    AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
    factory.addAspect(new AuditLogAspect(mockLogger, new ObjectMapper()));
    proxy = factory.getProxy();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void successfulMethod_logsEventWithSuccessTrue() {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofUser(7L, "gateway", List.of()));

    proxy.createOrder("test");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    AuditEvent event = captor.getValue();
    assertThat(event.getAction()).isEqualTo("CREATE_ORDER");
    assertThat(event.getResource()).isEqualTo("ORDER");
    assertThat(event.isSuccess()).isTrue();
    assertThat(event.getErrorMessage()).isNull();
    assertThat(event.getUserId()).isEqualTo(7L);
    assertThat(event.getAfterJson()).contains("test");
    assertThat(event.getTimestamp()).isNotNull();
  }

  @Test
  void failingMethod_logsEventWithSuccessFalseAndRethrows() {
    assertThatThrownBy(() -> proxy.failingMethod())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("intentional failure");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    AuditEvent event = captor.getValue();
    assertThat(event.isSuccess()).isFalse();
    assertThat(event.getErrorMessage()).isEqualTo("intentional failure");
    assertThat(event.getAfterJson()).isNull();
  }

  @Test
  void withSystemContext_logsNullUserId() {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofSystem("billing-service"));

    proxy.createOrder("x");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    assertThat(captor.getValue().getUserId()).isNull();
  }

  @Test
  void unauthenticated_logsNullUserId() {
    proxy.createOrder("x");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    assertThat(captor.getValue().getUserId()).isNull();
  }

  @Test
  void loggerThrowingException_doesNotBubbleUp() {
    // AuditLogger.log() throws — but primary call should still succeed
    doThrow(new RuntimeException("audit db down")).when(mockLogger).log(any());

    assertThatCode(() -> proxy.createOrder("test")).doesNotThrowAnyException();
  }

  @Test
  void returnValue_isSerializedToAfterJson() {
    proxy.createOrder("my-order");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    assertThat(captor.getValue().getAfterJson()).contains("my-order");
  }

  @Test
  void voidMethod_afterJsonIsNull() {
    proxy.voidMethod();

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(mockLogger).log(captor.capture());

    assertThat(captor.getValue().getAfterJson()).isNull();
  }

  // ─── test target ───

  static class SampleService {

    @AuditLog(action = "CREATE_ORDER", resource = "ORDER")
    public String createOrder(String name) {
      return "order:" + name;
    }

    @AuditLog(action = "FAIL_OP", resource = "ORDER")
    public void failingMethod() {
      throw new RuntimeException("intentional failure");
    }

    @AuditLog(action = "VOID_OP", resource = "ORDER")
    public void voidMethod() {
      // returns void
    }
  }
}
