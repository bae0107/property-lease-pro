package com.jugu.propertylease.device.common.entity.dto;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "表类数据结构")
public class MeterInfoDTO {

  @Schema(description = "表主键Id（业务内部）")
  private long id;

  @Schema(description = "安装类型(1=独立表、2=公摊表)")
  private int installType;

  // 云丁填写sn
  // 注意别的供应商的meterNo是否为采集器ID
  @Schema(description = "表号（第三方）合一的表号和deviceId一样")
  private String meterNo;

  @Schema(description = "绑定的房源的编号，空为无绑定")
  private String boundRoomId;

  @Schema(description = "绑定、换绑、解绑时间")
  private String boundTimeChange;

  @Schema(description = "当前读数（第三方）")
  private double consumeAmount;

  @Schema(description = "读数时间（第三方提供）")
  private String consumeRecordTime;

  @Schema(description = "计费周期开始的读数时间（第三方提供）")
  private String periodConsumeStartTime;

  @Schema(description = "计费周期开始的读数（第三方提供）")
  private double periodConsumeAmount;

  @Schema(description = "状态(1=未安装、2=离线、3=在线)")
  @SerializedName("status")
  private int status;

  @Schema(description = "状态(1=未安装、2=离线、3=在线)更新时间，合一没有")
  @SerializedName("statusTime")
  private String statusTime;

  @Schema(description = "第三方内部id（第三方接口查询用）")
  private String deviceId;

  // 暂时先不用
  @Schema(description = "字典表id：设备型号ID")
  private String deviceModelId;

  @Schema(description = "第三方服务商名称（云丁、合一等）")
  private String providerName;

  @Schema(description = "第三方服务商op值")
  private int providerOp;

  // 云丁填写sn
  @Schema(description = "采集器编号,云丁写SN，合一目前没找到")
  private String collectorId;

  @Schema(description = "创建人Id")
  private String createUserId;

  @Schema(description = "创建人名称")
  private String createUserName;

  @Schema(description = "创建时间")
  private String createTime;

  @Schema(description = "更新人Id")
  private String updateUserId;

  @Schema(description = "更新人姓名")
  private String updateUserName;

  @Schema(description = "更新时间")
  private String updateTime;

  @Schema(description = "删除标记(1=正常、2=删除)")
  @SerializedName("isDeleted")
  private int isDeleted;

  @Schema(description = "删除人Id")
  private String deleteUserId;

  @Schema(description = "删除人名称")
  private String deleteUserName;

  @Schema(description = "删除时间")
  private String deleteTime;
}
