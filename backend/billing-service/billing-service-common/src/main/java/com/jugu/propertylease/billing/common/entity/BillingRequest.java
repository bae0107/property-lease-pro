package com.jugu.propertylease.billing.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "账单创建")
public class BillingRequest {

  @Schema(description = "用户ID")
  private String userId;

  @Schema(description = "总金额，分")
  private int amountCent;

  @Schema(description = "账单类型（按业务自定义）")
  private String billingType;
}
