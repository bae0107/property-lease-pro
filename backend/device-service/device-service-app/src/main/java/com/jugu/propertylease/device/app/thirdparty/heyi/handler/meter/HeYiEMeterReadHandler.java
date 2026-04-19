package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;

public class HeYiEMeterReadHandler extends HeYiMeterReadHandler<EMeterCheckResponse> {

  public HeYiEMeterReadHandler(String deviceId) {
    super(deviceId);
  }

  @Override
  public EMeterCheckResponse parseFailResult(ErrorInfo errorInfo) {
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    checkResponse.failResponse(errorInfo);
    return checkResponse;
  }

  @Override
  public EMeterCheckResponse parseSuccessResult(HeYiMeterCheckResponse responseObject) {
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    float amount = Float.parseFloat(String.valueOf(responseObject.getConsumeAmount()));
    String time = Common.findTimeByMillSecondTimestamp(
        Long.parseLong(responseObject.getRecordTime()));
    checkResponse.successReadConsumeResponse(amount, time);
    return checkResponse;
  }
}
