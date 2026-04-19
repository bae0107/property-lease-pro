package com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.request.YunDingLockPwdOpRequest;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingCallBackResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class YunDingLockPwdOpHandler extends
    YunDingRequestHandler<DeviceResponse, YunDingCallBackResponse> {

  private final String deviceId;

  private final int pwdId;

  private final YunDingUrlE urlE;

  @Override
  public DeviceResponse handleTokenError() {
    DeviceResponse deviceResponse = new DeviceResponse();
    deviceResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return deviceResponse;
  }

  @Override
  public DeviceResponse handleRequest(String token, String domain,
      Sender<YunDingCallBackResponse> sender, String serviceName) {
    YunDingLockPwdOpRequest opRequest = new YunDingLockPwdOpRequest(pwdId, deviceId, token);
    String requestJson = GsonFactory.toJson(opRequest);
    String url = urlE.getUrl();
    Optional<YunDingCallBackResponse> callBackResponseOptional = sender.sendPostRequest(
        domain + url, requestJson, YunDingCallBackResponse.class);
    DeviceResponse deviceResponse = new DeviceResponse();
    deviceResponse.setDeviceId(deviceId);
    if (callBackResponseOptional.isPresent()) {
      YunDingCallBackResponse callBackResponse = callBackResponseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          callBackResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        deviceResponse.failResponse(errorInfo.get());
        return deviceResponse;
      }
      if (callBackResponse.isValid()) {
        parseSuccessResult(callBackResponse, deviceResponse);
        deviceResponse.setServiceNote(urlE.getCallBackServiceName());
        return deviceResponse;
      }
      ErrorTypesE errorType = ErrorTypesE.THIRD_PARTY_ERROR;
      String name = urlE.getName();
      log.error("yun ding pwd op failed due to serviceId empty! DeviceId: {}, op:{}", deviceId,
          name);
      deviceResponse.failResponse(
          new ErrorInfo(errorType.getErrorCode(), String.format(errorType.getErrorMsg(),
              serviceName, name, "第三方无识别", "返回的serviceId无效")));
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
