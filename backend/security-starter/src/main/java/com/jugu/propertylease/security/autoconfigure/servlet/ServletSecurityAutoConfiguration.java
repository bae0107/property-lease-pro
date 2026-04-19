package com.jugu.propertylease.security.autoconfigure.servlet;

import com.jugu.propertylease.security.authorization.SecurityPermissionEvaluator;
import com.jugu.propertylease.security.autoconfigure.SecurityStarterAutoConfiguration;
import com.jugu.propertylease.security.datapermission.DataPermissionInterceptor;
import com.jugu.propertylease.security.filter.servlet.ServletServiceJwtFilter;
import com.jugu.propertylease.security.filter.servlet.TraceIdFilter;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet 侧安全自动装配。
 *
 * <p>注意：本类不实现 WebMvcConfigurer，也不在构造器注入 DataPermissionInterceptor，
 * 避免"自身 @Bean 工厂方法 + 构造器注入同一类型"产生循环依赖。 拦截器注册通过独立的 @Bean WebMvcConfigurer 完成。
 */
@AutoConfiguration(after = SecurityStarterAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "security.mode", havingValue = "service")
@Import(MethodSecurityConfiguration.class)
public class ServletSecurityAutoConfiguration {

  @Bean
  public TraceIdFilter traceIdFilter() {
    return new TraceIdFilter();
  }

  @Bean
  public ServletServiceJwtFilter servletServiceJwtFilter(JwtTokenParser jwtTokenParser,
      SecurityProperties securityProperties) {
    return new ServletServiceJwtFilter(jwtTokenParser, securityProperties);
  }

  @Bean
  public SecurityPermissionEvaluator securityPermissionEvaluator() {
    return new SecurityPermissionEvaluator();
  }

  @Bean
  public DataPermissionInterceptor dataPermissionInterceptor() {
    return new DataPermissionInterceptor();
  }

  /**
   * 独立 WebMvcConfigurer Bean，接收已完成创建的 DataPermissionInterceptor 并注册到 MVC。 与
   * servletSecurityAutoConfiguration Bean 的初始化完全解耦，不产生循环依赖。
   */
  @Bean
  public WebMvcConfigurer dataPermissionMvcConfigurer(DataPermissionInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
      }
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      TraceIdFilter traceIdFilter,
      ServletServiceJwtFilter jwtFilter,
      SecurityProperties props) throws Exception {
    // 使用 AntPathRequestMatcher 而非默认的 MvcRequestMatcher。
    // MvcRequestMatcher 依赖 HandlerMappingIntrospector Bean，在没有完整 Spring MVC
    // 上下文的场景（如单元测试的 WebApplicationContextRunner）会启动失败。
    // AntPathRequestMatcher 只做路径模式匹配，无额外依赖，行为等价。
    AntPathRequestMatcher[] permitMatchers = props.getEffectivePermitPaths().stream()
        .map(AntPathRequestMatcher::new)
        .toArray(AntPathRequestMatcher[]::new);

    http
        .csrf(csrf -> csrf.disable())
        // 微服务只通过 Gateway 接收请求，CORS 统一由 Gateway 层处理（GW-C-06）。
        // 禁用 Spring Security 内置的 CorsFilter，防止其拦截 Gateway 透传的
        // Origin Header 并在 SecurityFilterChain 之前直接返回 403。
        .cors(cors -> cors.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(permitMatchers).permitAll()
            .anyRequest().authenticated())
        // addFilterBefore/After 的参照类必须是 Spring Security 内置的已注册 Filter，
        // 不能使用自定义 Filter 类作参照（会抛 "does not have a registered order"）。
        //
        // 执行顺序：SecurityContextHolderFilter(≈200) → TraceIdFilter → ... →
        //           JwtFilter → UsernamePasswordAuthenticationFilter(≈800)
        //
        // TraceIdFilter 紧跟 SecurityContextHolderFilter 之后（链路最前端），
        // 确保后续任何 Filter（含 jwtFilter）写 401 时 MDC 中已有 traceId。
        .addFilterAfter(traceIdFilter, SecurityContextHolderFilter.class)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new SecurityAuthenticationEntryPoint())
            .accessDeniedHandler(new SecurityAccessDeniedHandler()));

    return http.build();
  }
}
