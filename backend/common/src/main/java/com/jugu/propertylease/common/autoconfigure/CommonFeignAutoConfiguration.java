package com.jugu.propertylease.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.feign.FeignBusinessExceptionErrorDecoder;
import com.jugu.propertylease.common.feign.FeignExceptionAspect;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Feign 集成自动装配：仅在 classpath 存在 Feign 时激活。
 *
 * <p>注册两个 Bean：
 * <ul>
 *   <li>{@link FeignBusinessExceptionErrorDecoder}：处理收到 HTTP 响应的失败</li>
 *   <li>{@link FeignExceptionAspect}：处理连接失败（RetryableException）</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.codec.ErrorDecoder")
public class CommonFeignAutoConfiguration {

  @Bean
  public ErrorDecoder feignBusinessExceptionErrorDecoder(ObjectMapper objectMapper) {
    return new FeignBusinessExceptionErrorDecoder(objectMapper);
  }

  @Bean
  public FeignExceptionAspect feignExceptionAspect() {
    return new FeignExceptionAspect();
  }
}
