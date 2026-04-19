package com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity.YunDingEMeterPeriodResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;
import java.util.Optional;

public class YunDingPeriodUsageHandler extends
    YunDingRequestHandler<EMeterPeriodResponse, YunDingEMeterPeriodResponse> {

  private final String deviceId;

  private final long startTime;

  private final long endTime;

  public YunDingPeriodUsageHandler(String deviceId, long startTime, long endTime) {
    this.deviceId = deviceId;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  @Override
  public EMeterPeriodResponse handleTokenError() {
    EMeterPeriodResponse checkResponse = new EMeterPeriodResponse();
    checkResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return checkResponse;
  }

  @Override
  public EMeterPeriodResponse handleRequest(String token, String domain,
      Sender<YunDingEMeterPeriodResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.CHECK_ELE_PERIOD_USAGE;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId, startTime, endTime);
    Optional<YunDingEMeterPeriodResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingEMeterPeriodResponse.class);
    EMeterPeriodResponse periodResponse = new EMeterPeriodResponse();
    periodResponse.setDeviceId(deviceId);
    if (responseOptional.isPresent()) {
      YunDingEMeterPeriodResponse yunDingEMeterPeriodResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          yunDingEMeterPeriodResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        periodResponse.failResponse(errorInfo.get());
        return periodResponse;
      }
      parseSuccessResult(yunDingEMeterPeriodResponse, periodResponse);
      return periodResponse;
    }
    periodResponse.failResponse(genConnectionErrorInfo(url));
    return periodResponse;
  }

  @Override
  public void parseSuccessResult(YunDingEMeterPeriodResponse yunDingResponse,
      EMeterPeriodResponse meterResponse) {
    String startTime = Common.findTimeBySecondTimestamp(yunDingResponse.getStartTime());
    String endTime = Common.findTimeBySecondTimestamp(yunDingResponse.getEndTime());
    meterResponse.successPeriodResponse(yunDingResponse.getConsumption(), startTime, endTime);
  }
}
