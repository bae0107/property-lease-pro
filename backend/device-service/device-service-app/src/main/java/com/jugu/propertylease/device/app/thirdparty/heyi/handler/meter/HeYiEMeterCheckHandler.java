package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import java.util.Locale;

public class HeYiEMeterCheckHandler extends HeYiMeterCheckHandler<EMeterCheckResponse> {

  public HeYiEMeterCheckHandler(String deviceId) {
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
    String recordTime = Common.findTimeSecondByISO(responseObject.getRecordTime());
    String enable = responseObject.getValveStatus().trim().toLowerCase(Locale.ROOT);
    int enableStatus;
    if ("open".equals(enable)) {
      enableStatus = EMeterSwitchResponse.SwitchTypeE.OPEN.getSwitchType();
    } else if ("close".equals(enable)) {
      enableStatus = EMeterSwitchResponse.SwitchTypeE.CLOSE.getSwitchType();
    } else {
      enableStatus = -1;
    }
    String enableTime = Common.findTimeSecondByISO(responseObject.getValveTime());
    checkResponse.successFullInfoResponse(amount, recordTime, enableStatus, enableTime,
        responseObject.convertStatus(),
        "", 0, "");
    return checkResponse;
  }
}
