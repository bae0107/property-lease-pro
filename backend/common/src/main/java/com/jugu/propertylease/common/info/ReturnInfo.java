package com.jugu.propertylease.common.info;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "执行无响应数据的请求")
public class ReturnInfo {

  @Schema(description = "统一响应的错误信息结构体")
  private ErrorInfo errorInfo;

  @Schema(description = "请求是否成功")
  private boolean isSuccess;

  public ReturnInfo(ErrorInfo errorInfo, boolean isSuccess) {
    this.errorInfo = errorInfo;
    this.isSuccess = isSuccess;
  }

  public ReturnInfo() {
  }

  public static ReturnInfo success() {
    return new ReturnInfo(null, true);
  }

  public static ReturnInfo fail(ErrorInfo error) {
    return new ReturnInfo(error, false);
  }

  public static ReturnInfo failByType(ErrorType errorType, String extraExplain) {
    String errorMsg = errorType.getErrorMsg();
    String msg = String.format(errorMsg, extraExplain);
    return fail(new ErrorInfo(errorType.getErrorCode(), msg));
  }

  public static ReturnInfo failByType(ErrorType errorType) {
    return fail(new ErrorInfo(errorType.getErrorCode(), errorType.getErrorMsg()));
  }
}
