package com.jugu.propertylease.common.info;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Schema(description = "返回数据结构体")
public class ReturnDataInfo<T> extends ReturnInfo {

  @Schema(description = "响应数据")
  private T responseData;

  public ReturnDataInfo(T responseData, ErrorInfo errorInfo, boolean isSuccess) {
    super(errorInfo, isSuccess);
    this.responseData = responseData;
  }

  public ReturnDataInfo() {
  }

  public static <T> ReturnDataInfo<T> failData(ErrorInfo error) {
    return new ReturnDataInfo<>(null, error, false);
  }

  public static <T> ReturnDataInfo<T> successData(T data) {
    return new ReturnDataInfo<>(data, null, true);
  }

  public static <T> ReturnDataInfo<T> failDataByType(ErrorType errorType, String extraExplain) {
    String errorMsg = errorType.getErrorMsg();
    String msg = String.format(errorMsg, extraExplain);
    return failData(new ErrorInfo(errorType.getErrorCode(), msg));
  }

  public static <T> ReturnDataInfo<T> failDataByType(ErrorType errorType) {
    return failData(new ErrorInfo(errorType.getErrorCode(), errorType.getErrorMsg()));
  }
}
