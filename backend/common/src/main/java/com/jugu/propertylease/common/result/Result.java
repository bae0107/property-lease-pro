package com.jugu.propertylease.common.result;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ErrorResponse;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * 函数式结果包装，供需要显式处理成功/失败两个分支的调用方使用。
 *
 * <p>与直接调用 FeignClient 的区别：
 * <ul>
 *   <li>直接调用：成功返回 {@code T}，失败抛 {@link BusinessException}（RuntimeException，可不处理）</li>
 *   <li>{@code Result.of}：成功/失败均封装在 {@code Result<T>} 中，调用方可用 API 链式处理</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * // 失败给出默认值
 * User user = Result.of(() -> userClient.getUserById(id))
 *     .getOrElseGet(err -> User.guest());
 *
 * // 先处理错误副作用，再给默认值
 * User user = Result.of(() -> userClient.getUserById(id))
 *     .onFailure(err -> log.warn("查询用户失败: {}", err.getCode()))
 *     .getOrElseGet(err -> User.guest());
 * </pre>
 *
 * <p>{@code Result.of} 只捕获 {@link BusinessException}，前提是所有 FeignClient 调用
 * 已通过 {@code FeignExceptionAspect} 将底层异常统一转换为 {@link BusinessException}。
 *
 * @param <T> 成功时的业务数据类型
 */
public final class Result<T> {

  private final T data;
  private final ErrorResponse error;
  private final HttpStatus status;

  private Result(T data, ErrorResponse error, HttpStatus status) {
    this.data = data;
    this.error = error;
    this.status = status;
  }

  // ===== 工厂方法 =====

  /**
   * 成功结果，status = 200。
   */
  public static <T> Result<T> ok(T data) {
    return new Result<>(data, null, HttpStatus.OK);
  }

  /**
   * 失败结果。
   */
  public static <T> Result<T> fail(ErrorResponse error, HttpStatus status) {
    return new Result<>(null, error, status);
  }

  /**
   * 执行 {@code supplier}，捕获 {@link BusinessException} 并包装为失败 {@code Result}。
   *
   * <p>注意：只捕获 {@link BusinessException}，其他异常继续向上传播。
   * 在使用前应确保 {@code FeignExceptionAspect} 已将所有底层异常转换为 {@link BusinessException}。
   */
  public static <T> Result<T> of(Supplier<T> supplier) {
    try {
      return ok(supplier.get());
    } catch (BusinessException e) {
      return fail(e.toErrorResponse(null), e.getHttpStatus());
    }
  }

  // ===== 查询方法 =====

  public boolean isSuccess() {
    return error == null;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /**
   * 成功时返回业务数据，失败时返回 null（调用方自行判断）。
   */
  public T getData() {
    return data;
  }

  /**
   * 失败时返回错误详情，成功时返回 null。
   */
  public ErrorResponse getError() {
    return error;
  }

  // ===== 链式 API =====

  /**
   * 失败时执行副作用（如打日志、上报监控），返回 {@code this} 支持链式调用。 成功时忽略，直接透传。
   */
  public Result<T> onFailure(Consumer<ErrorResponse> handler) {
    if (!isSuccess() && handler != null) {
      handler.accept(error);
    }
    return this;
  }

  /**
   * 成功时返回业务数据；失败时执行 {@code fallback} 并返回其结果。 类比 {@code Optional.orElseGet}，可在 {@code fallback}
   * 中构建默认值或降级结果。
   *
   * <p>与 {@link #onFailure} 搭配：先用 {@code onFailure} 处理副作用，
   * 再用 {@code getOrElseGet} 提供默认值，两者职责分离。
   */
  public T getOrElseGet(Function<ErrorResponse, T> fallback) {
    if (isSuccess()) {
      return data;
    }
    return fallback != null ? fallback.apply(error) : null;
  }
}
