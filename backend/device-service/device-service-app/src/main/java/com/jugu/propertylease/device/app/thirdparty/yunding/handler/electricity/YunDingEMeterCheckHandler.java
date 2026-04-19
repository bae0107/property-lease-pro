package com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity.YunDingEMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import java.util.Optional;

public abstract class YunDingEMeterCheckHandler extends
    YunDingRequestHandler<EMeterCheckResponse, YunDingEMeterCheckResponse> {

  private final String deviceId;

  public YunDingEMeterCheckHandler(String deviceId) {
    this.deviceId = deviceId;
  }

  @Override
  public EMeterCheckResponse handleTokenError() {
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    checkResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return checkResponse;
  }

  @Override
  public EMeterCheckResponse handleRequest(String token, String domain,
      Sender<YunDingEMeterCheckResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.READ_ELE_METER;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId);
    Optional<YunDingEMeterCheckResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingEMeterCheckResponse.class);
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    checkResponse.setDeviceId(deviceId);
    if (responseOptional.isPresent()) {
      YunDingEMeterCheckResponse yunDingEMeterCheckResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          yunDingEMeterCheckResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        checkResponse.failResponse(errorInfo.get());
        return checkResponse;
      }
      parseSuccessResult(yunDingEMeterCheckResponse, checkResponse);
      return checkResponse;
    }
    checkResponse.failResponse(genConnectionErrorInfo(url));
    return checkResponse;
  }
}
