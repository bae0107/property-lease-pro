package com.jugu.propertylease.security.filter.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import com.jugu.propertylease.security.properties.SecurityProperties;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class MockUserFilterTest {

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_delegatesToFilterChain() throws Exception {
    MockUserFilter filter = new MockUserFilter(new SecurityProperties.MockUser());
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_injectsMockUserIntoSecurityContextDuringChain() throws Exception {
    SecurityProperties.MockUser mockUser = new SecurityProperties.MockUser();
    mockUser.setUserId(99L);
    mockUser.setPermissions(List.of("admin:all"));

    MockUserFilter filter = new MockUserFilter(mockUser);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<ServiceJwtAuthenticationToken> captured = new AtomicReference<>();
    FilterChain chain = (req, res) ->
        captured.set((ServiceJwtAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().getUserId()).isEqualTo(99L);
    assertThat(captured.get().getCallerName()).isEqualTo("mock");
    assertThat(captured.get().isAuthenticated()).isTrue();
  }

  @Test
  void doFilter_configuredPermissions_areSetOnAuthentication() throws Exception {
    SecurityProperties.MockUser mockUser = new SecurityProperties.MockUser();
    mockUser.setUserId(1L);
    mockUser.setPermissions(List.of("order:read", "order:write", "order:delete"));

    MockUserFilter filter = new MockUserFilter(mockUser);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<ServiceJwtAuthenticationToken> captured = new AtomicReference<>();
    FilterChain chain = (req, res) ->
        captured.set((ServiceJwtAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(captured.get().getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("order:read", "order:write", "order:delete");
  }

  @Test
  void doFilter_defaultMockUser_hasUserId1AndEmptyPermissions() throws Exception {
    MockUserFilter filter = new MockUserFilter(new SecurityProperties.MockUser());
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<ServiceJwtAuthenticationToken> captured = new AtomicReference<>();
    FilterChain chain = (req, res) ->
        captured.set((ServiceJwtAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(captured.get().getUserId()).isEqualTo(1L);
    assertThat(captured.get().getAuthorities()).isEmpty();
  }

  @Test
  void doFilter_clearsSecurityContextAfterChainCompletes() throws Exception {
    MockUserFilter filter = new MockUserFilter(new SecurityProperties.MockUser());
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    // finally 块应清理 SecurityContext，防止线程复用时污染
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doFilter_clearsSecurityContextEvenWhenChainThrows() throws Exception {
    MockUserFilter filter = new MockUserFilter(new SecurityProperties.MockUser());
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      throw new RuntimeException("chain error");
    };

    try {
      filter.doFilter(request, response, chain);
    } catch (RuntimeException ignored) {
      // 预期异常
    }

    // 即使 chain 抛异常，finally 块也应清理 SecurityContext
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
