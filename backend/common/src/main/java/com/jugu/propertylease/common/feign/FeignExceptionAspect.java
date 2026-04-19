package com.jugu.propertylease.common.feign;

import com.jugu.propertylease.common.exception.BusinessException;
import feign.RetryableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * 全局 Feign 异常转换切面：确保所有 FeignClient 方法调用只向外抛出 {@link BusinessException}。
 *
 * <p>与 {@link FeignBusinessExceptionErrorDecoder} 的分工：
 * <ul>
 *   <li>{@link FeignBusinessExceptionErrorDecoder}：处理收到 HTTP 响应的失败（4xx/5xx），
 *       将响应体反序列化为 {@link BusinessException}</li>
 *   <li>{@link FeignExceptionAspect}：处理未收到 HTTP 响应的失败（连接拒绝、超时等），
 *       将 {@link RetryableException} 转换为 {@link BusinessException}(503)</li>
 * </ul>
 *
 * <p>两者组合后，调用方无论遇到什么 Feign 失败，拿到的都是 {@link BusinessException}，
 * 保证异常体系的一致性，{@code Result.of} 只需捕获 {@link BusinessException} 即可覆盖全部场景。
 */
@Aspect
public class FeignExceptionAspect {

  private static final Logger log = LoggerFactory.getLogger(FeignExceptionAspect.class);

  @Around("@within(org.springframework.cloud.openfeign.FeignClient)")
  public Object convertFeignException(ProceedingJoinPoint pjp) throws Throwable {
    try {
      return pjp.proceed();
    } catch (BusinessException e) {
      // ErrorDecoder 已转换为 BusinessException，直接透传
      throw e;
    } catch (RetryableException e) {
      // 连接失败、网络超时等，无 HTTP 响应，ErrorDecoder 不处理
      log.warn("Feign 连接失败（RetryableException）: {}", e.getMessage());
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "SERVICE_UNAVAILABLE",
          "下游服务暂时不可用，请稍后重试",
          e);
    } catch (Exception e) {
      // 兜底：其他未预期 Feign 异常
      log.warn("Feign 未预期异常: {}", e.getMessage());
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "SERVICE_UNAVAILABLE",
          "下游服务暂时不可用，请稍后重试",
          e);
    }
  }
}
