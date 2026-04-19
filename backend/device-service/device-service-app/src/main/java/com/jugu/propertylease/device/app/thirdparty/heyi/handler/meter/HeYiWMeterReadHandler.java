package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;

public class HeYiWMeterReadHandler extends HeYiMeterReadHandler<WMeterCheckResponse> {

  public HeYiWMeterReadHandler(String deviceId) {
    super(deviceId);
  }

  @Override
  public WMeterCheckResponse parseFailResult(ErrorInfo errorInfo) {
    WMeterCheckResponse checkResponse = new WMeterCheckResponse();
    checkResponse.failResponse(errorInfo);
    return checkResponse;
  }

  @Override
  public WMeterCheckResponse parseSuccessResult(HeYiMeterCheckResponse responseObject) {
    WMeterCheckResponse checkResponse = new WMeterCheckResponse();
    String time = Common.findTimeByMillSecondTimestamp(
        Long.parseLong(responseObject.getRecordTime()));
    checkResponse.successFullInfoResponse(responseObject.getConsumeAmount(), time, 0, "", 1, 0);
    return checkResponse;
  }
}
