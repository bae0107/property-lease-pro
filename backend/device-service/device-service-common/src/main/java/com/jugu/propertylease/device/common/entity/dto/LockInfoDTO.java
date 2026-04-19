package com.jugu.propertylease.device.common.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "门锁数据结构")
public class LockInfoDTO {

  @Schema(description = "门锁主键Id（业务内部）")
  private long id;

  @Schema(description = "绑定的房源的编号，空为无绑定")
  private String boundRoomId;

  @Schema(description = "设备UUId")
  private String deviceId;

  @Schema(description = "门锁名称")
  private String name;

  @Schema(description = "门锁Mac")
  private String mac;

  @Schema(description = "门锁SN")
  private String sn;

  @Schema(description = "门锁设备密钥")
  private String authKey;

  @Schema(description = "产品型号")
  private String productModel;

  @Schema(description = "固件版本")
  private String hw;

  @Schema(description = "设备状态(1=正常、2=低电量)")
  private int status;

  @Schema(description = "在线状态(1=在线、2=离线)")
  private int onlineStatus;

  @Schema(description = "在线离线更新时间")
  private String onlineStatusTime;

  @Schema(description = "信号")
  private int lockSignal;

  @Schema(description = "电量")
  private int electricity;

  @Schema(description = "门锁SIM卡ccid号")
  private String ccid;

  @Schema(description = "门锁软件版本号")
  private String sw;

  @Schema(description = "第三方服务商名称（云丁、合一等）")
  private String providerName;

  @Schema(description = "第三方服务商op值")
  private int providerOp;

  @Schema(description = "创建人")
  private String createUserId;

  @Schema(description = "创建人名称")
  private String createUserName;

  @Schema(description = "创建时间")
  private String createTime;

  @Schema(description = "修改人")
  private String updateUserId;

  @Schema(description = "更新人名称")
  private String updateUserName;

  @Schema(description = "更新时间")
  private String updateTime;

  @Schema(description = "删除标记(1=正常、2=删除)")
  private int isDeleted;

  @Schema(description = "删除人")
  private String deleteUserId;

  @Schema(description = "删除人名称")
  private String deleteUserName;

  @Schema(description = "删除时间")
  private String deleteTime;
}
