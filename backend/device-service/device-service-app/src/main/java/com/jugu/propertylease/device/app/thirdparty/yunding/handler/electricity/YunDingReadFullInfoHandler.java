package com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity.YunDingEMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;

public class YunDingReadFullInfoHandler extends YunDingEMeterCheckHandler {

  public YunDingReadFullInfoHandler(String deviceId) {
    super(deviceId);
  }

  @Override
  public void parseSuccessResult(YunDingEMeterCheckResponse yunDingResponse,
      EMeterCheckResponse meterResponse) {
    String recordTime = Common.findTimeByMillSecondTimestamp(yunDingResponse.getRecordTime());
    String enableStateTime = Common.findTimeByMillSecondTimestamp(
        yunDingResponse.getEnableStateTime());
    String onOffTime = Common.findTimeByMillSecondTimestamp(yunDingResponse.getOnOffTime());
    String transTime = Common.findTimeByMillSecondTimestamp(yunDingResponse.getTransStatusTime());
    meterResponse.successFullInfoResponse(yunDingResponse.getConsumeAmount(), recordTime,
        yunDingResponse.getEnableState(),
        enableStateTime, yunDingResponse.getOnOffLine(), onOffTime,
        yunDingResponse.getTransStatus(), transTime);
  }
}
