package com.jugu.propertylease.device.app.meter;

import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;

public class MeterUtil {

  public static MeterSwitchStatus findMiddleStatusBySwitchType(
      EMeterSwitchResponse.SwitchTypeE switchTypeE) {
    return switchTypeE == EMeterSwitchResponse.SwitchTypeE.OPEN ? MeterSwitchStatus.OPENING
        : MeterSwitchStatus.CLOSING;
  }
}
