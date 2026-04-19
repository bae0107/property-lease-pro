package com.jugu.propertylease.common.info;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "统一响应的错误信息结构体")
@NoArgsConstructor
public class ErrorInfo {

  @Schema(description = "错误码")
  private int errorCode;

  @Schema(description = "错误信息")
  private String errorMsg;

  public ErrorInfo(int errorCode, String errorMsg) {
    this.errorCode = errorCode;
    this.errorMsg = errorMsg;
  }
}
