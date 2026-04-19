package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;

public class HeYiWMeterCheckHandler extends HeYiMeterCheckHandler<WMeterCheckResponse> {

  public HeYiWMeterCheckHandler(String deviceId) {
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
    int meterType = responseObject.getMeterType();
    int type;
    if (meterType == 10) {
      type = 1;
    } else if (meterType == 11) {
      type = 2;
    } else {
      type = -1;
    }
    checkResponse.successFullInfoResponse(responseObject.getConsumeAmount(),
        Common.findTimeSecondByISO(responseObject.getRecordTime()),
        responseObject.convertStatus(), "", type, -1);
    return checkResponse;
  }
}
