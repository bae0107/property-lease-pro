package com.jugu.propertylease.device.app.thirdparty.yunding.handler.water;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingCallBackResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class YunDingWMeterRecordHandler extends
    YunDingRequestHandler<DeviceResponse, YunDingCallBackResponse> {

  private final String deviceId;

  @Override
  public DeviceResponse handleTokenError() {
    WMeterCheckResponse checkResponse = new WMeterCheckResponse();
    checkResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return checkResponse;
  }

  @Override
  public DeviceResponse handleRequest(String token, String domain,
      Sender<YunDingCallBackResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.READ_WATER_METER;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId);
    Optional<YunDingCallBackResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingCallBackResponse.class);
    DeviceResponse deviceResponse = new DeviceResponse();
    deviceResponse.setDeviceId(deviceId);
    deviceResponse.setServiceNote(urlE.getCallBackServiceName());
    if (responseOptional.isPresent()) {
      YunDingCallBackResponse callBackResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          callBackResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        deviceResponse.failResponse(errorInfo.get());
        return deviceResponse;
      }
      if (callBackResponse.isValid()) {
        parseSuccessResult(callBackResponse, deviceResponse);
        return deviceResponse;
      }
      ErrorTypesE errorType = ErrorTypesE.THIRD_PARTY_ERROR;
      log.error("yun ding record water meter failed due to serviceId empty! DeviceId: {}",
          deviceId);
      deviceResponse.failResponse(
          new ErrorInfo(errorType.getErrorCode(), String.format(errorType.getErrorMsg(),
              serviceName, urlE.getName(), "第三方无识别", "返回的serviceId无效")));
      return deviceResponse;
    }
    deviceResponse.failResponse(genConnectionErrorInfo(url));
    return deviceResponse;
  }

  @Override
  public void parseSuccessResult(YunDingCallBackResponse yunDingResponse,
      DeviceResponse deviceResponse) {
    deviceResponse.setServiceId(yunDingResponse.getServiceId());
    deviceResponse.setSuccess(true);
  }
}
