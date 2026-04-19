package com.jugu.propertylease.device.app.thirdparty.heyi;

import lombok.Getter;

@Getter
public enum HeYiUrlE {
  ACCESS_TOKEN("获取通行证", "/account/oauth/token", ""),
  CHECK_METER("查询表类信息", "/amr/v1/meters/%s", ""),
  READ_BATCH_METERS("批量抄表", "/amr/v1/batchMeterRead", ""),
  READ_METER("单表抄表", "/amr/v1/meterRead", ""),
  ELE_SWITCH("电表开关闸", "/amr/v1/valveCtr", "");

  private final String name;

  private final String url;

  private final String callBackServiceName;

  HeYiUrlE(String name, String url, String callBackServiceName) {
    this.name = name;
    this.url = url;
    this.callBackServiceName = callBackServiceName;
  }
}
