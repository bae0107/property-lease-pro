package com.jugu.propertylease.billing.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@Schema(description = "账单详情结构")
public class BillingRecordDTO {

  @Schema(description = "充值账单主键")
  private String billingId;

  @Schema(description = "充值金额(分)")
  private int amountCent;

  @Schema(description = "账单状态")
  private String billingStatus;

  @Schema(description = "支付方式")
  private String payMethod;

  @Schema(description = "账单类型")
  private String billingType;

  @Schema(description = "创建人名称")
  private String createUserName;

  @Schema(description = "创建人ID")
  private String createUserId;

  @Schema(description = "创建时间")
  private String createTime;

  @Schema(description = "更新时间")
  private String updateTime;

  @Schema(description = "完成时间")
  private String completeTime;

  @Schema(description = "错误信息")
  private String errorInfo;

  @Schema(description = "指向支付拉起的使用参数")
  private int rootIndex;

  @Schema(description = "订单支付过期时间")
  private String expireTime;

  @Schema(description = "订单支付信息")
  private String payInfo;
}
