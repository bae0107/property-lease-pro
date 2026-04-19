package com.jugu.propertylease.common.autoconfigure;

import com.jugu.propertylease.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * common 模块自动装配：仅在 Servlet Web 应用中注册 {@link GlobalExceptionHandler}。 Gateway 是 WebFlux，不需要也不应该加载
 * MVC 的异常处理器。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonAutoConfiguration {

  @Bean
  public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
