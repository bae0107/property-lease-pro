package com.jugu.propertylease.device.app.entity.response.lock;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "门锁数据查询response结构")
public class LockCheckResponse extends DeviceResponse {

  @Schema(description = "门锁名称")
  private String name;

  @Schema(description = "门锁mac")
  private String mac;

  @Schema(description = "门锁sn")
  private String sn;

  @Schema(description = "门锁模型名称")
  private String productModel;

  @Schema(description = "门锁在线状态")
  private int onlineStatus;

  @Schema(description = "门锁在线状态刷新时间")
  private String onlineStatusTime;

  @Schema(description = "门锁信号")
  private int lockSignal;

  @Schema(description = "门锁电量")
  private int electricity;

  public void successLockCheckResponse(String name, String mac, String sn, String productModel,
      int onlineStatus,
      String onlineStatusTime, int lockSignal, int electricity) {
    super.setSuccess(true);
    this.name = name;
    this.mac = mac;
    this.sn = sn;
    this.productModel = productModel;
    this.onlineStatus = onlineStatus;
    this.onlineStatusTime = onlineStatusTime;
    this.lockSignal = lockSignal;
    this.electricity = electricity;
  }
}
