package com.jugu.propertylease.security.filter.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.jugu.propertylease.security.constants.SecurityConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Mock
  FilterChain chain;

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void existingTraceIdHeader_populatesMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(SecurityConstants.HEADER_TRACE_ID, "abc-123");

    // 在 chain.doFilter 被调用期间，验证 MDC 已写入
    doAnswer(inv -> {
      assertThat(MDC.get(TraceIdFilter.MDC_TRACE_KEY)).isEqualTo("abc-123");
      return null;
    }).when(chain).doFilter(any(), any());

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(chain).doFilter(any(), any());
  }

  @Test
  void missingTraceIdHeader_generatesFallbackTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(); // 不设 Header

    doAnswer(inv -> {
      String traceId = MDC.get(TraceIdFilter.MDC_TRACE_KEY);
      assertThat(traceId).isNotNull().isNotBlank();
      return null;
    }).when(chain).doFilter(any(), any());

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(chain).doFilter(any(), any());
  }

  @Test
  void blankTraceIdHeader_generatesFallbackTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(SecurityConstants.HEADER_TRACE_ID, "   ");

    doAnswer(inv -> {
      assertThat(MDC.get(TraceIdFilter.MDC_TRACE_KEY)).isNotBlank();
      return null;
    }).when(chain).doFilter(any(), any());

    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }

  @Test
  void afterFilterCompletes_mdcIsCleared() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(SecurityConstants.HEADER_TRACE_ID, "cleanup-test");

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    // 请求结束后 MDC 中不应再有 traceId
    assertThat(MDC.get(TraceIdFilter.MDC_TRACE_KEY)).isNull();
  }

  @Test
  void chainThrowsException_mdcIsStillCleared() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(SecurityConstants.HEADER_TRACE_ID, "error-trace");
    doThrow(new RuntimeException("downstream error")).when(chain).doFilter(any(), any());

    try {
      filter.doFilter(request, new MockHttpServletResponse(), chain);
    } catch (RuntimeException ignored) {
      // 预期内
    }

    // 即使下游抛异常，finally 块也必须清理 MDC
    assertThat(MDC.get(TraceIdFilter.MDC_TRACE_KEY)).isNull();
  }
}
