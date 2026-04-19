package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingLockCheckResponse extends YunDingError {

  @SerializedName("mac")
  private String mac;

  @SerializedName("sn")
  private String sn;

  @SerializedName("onoff_line")
  private int OnOffLine;

  @SerializedName("onoff_time")
  private int OnOffTime;

  @SerializedName(value = "power", alternate = {"battery"})
  private int electricity;

  @SerializedName("power_refreshtime")
  private int electricityTime;

  @SerializedName("lqi")
  private int lockSignal;

  @SerializedName("model")
  private String model;

  @SerializedName("name")
  private String name;

  public YunDingLockCheckResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
