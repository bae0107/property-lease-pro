package com.jugu.propertylease.device.common.entity.response;

import com.jugu.propertylease.common.info.ErrorInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "表类查询结果")
public class DeviceResponse {

  /**
   * 等待回调相应的id
   */
  @Schema(description = "回调服务Id（后台使用）")
  private String serviceId;

  @Schema(description = "回调服务Id主键（后台使用）")
  private long serviceKey;

  /**
   * 回调事件名称
   */
  @Schema(description = "回调服务事件名（后台使用）")
  private String serviceNote;

  @Schema(description = "设备ID")
  private String deviceId;

  @Schema(description = "查询是否成功")
  private boolean isSuccess;

  @Schema(description = "错误信息")
  private ErrorInfo errorInfo;

  public void failResponse(ErrorInfo errorInfo) {
    this.isSuccess = false;
    this.errorInfo = errorInfo;
  }
}
