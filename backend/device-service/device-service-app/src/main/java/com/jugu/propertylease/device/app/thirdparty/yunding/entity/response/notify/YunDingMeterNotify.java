package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.notify;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class YunDingMeterNotify {

  @SerializedName("id")
  private String id;

  @SerializedName("ErrNo")
  private int errorNo;

  @SerializedName("operation_type")
  private int opType;

  @SerializedName("amount")
  private float amount;

  @SerializedName("total_amount")
  private float totalAmount;

  @SerializedName("overdraft")
  private float overDraft;

  @SerializedName("capacity")
  private float capacity;
}
