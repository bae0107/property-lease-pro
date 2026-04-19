package com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity.YunDingEMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;

public class YunDingEMeterSwitchStateHandler extends YunDingEMeterCheckHandler {

  public YunDingEMeterSwitchStateHandler(String deviceId) {
    super(deviceId);
  }

  @Override
  public void parseSuccessResult(YunDingEMeterCheckResponse yunDingResponse,
      EMeterCheckResponse meterResponse) {
    String enableStateTime = Common.findTimeByMillSecondTimestamp(
        yunDingResponse.getEnableStateTime());
    meterResponse.successCheckEnableResponse(yunDingResponse.getEnableState(), enableStateTime);
  }
}
