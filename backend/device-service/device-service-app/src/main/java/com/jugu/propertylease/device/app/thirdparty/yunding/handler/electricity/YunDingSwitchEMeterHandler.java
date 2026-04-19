package com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.request.YunDingRequest;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingCallBackResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class YunDingSwitchEMeterHandler extends
    YunDingRequestHandler<EMeterSwitchResponse, YunDingCallBackResponse> {

  private final String deviceId;

  private final EMeterSwitchResponse.SwitchTypeE switchTypeE;

  public YunDingSwitchEMeterHandler(String deviceId, EMeterSwitchResponse.SwitchTypeE switchTypeE) {
    this.deviceId = deviceId;
    this.switchTypeE = switchTypeE;
  }

  @Override
  public EMeterSwitchResponse handleTokenError() {
    EMeterSwitchResponse switchResponse = new EMeterSwitchResponse();
    switchResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return switchResponse;
  }

  @Override
  public EMeterSwitchResponse handleRequest(String token, String domain,
      Sender<YunDingCallBackResponse> sender, String serviceName) {
    YunDingRequest yunDingRequest = new YunDingRequest(deviceId, token);
    String requestJson = GsonFactory.toJson(yunDingRequest);
    YunDingUrlE urlE =
        switchTypeE == EMeterSwitchResponse.SwitchTypeE.OPEN ? YunDingUrlE.ELE_SWITCH_ON
            : YunDingUrlE.ELE_SWITCH_OFF;
    String url = urlE.getUrl();
    Optional<YunDingCallBackResponse> callBackResponseOptional = sender.sendPostRequest(
        domain + url, requestJson, YunDingCallBackResponse.class);
    EMeterSwitchResponse switchResponse = new EMeterSwitchResponse();
    switchResponse.setDeviceId(deviceId);
    if (callBackResponseOptional.isPresent()) {
      YunDingCallBackResponse callBackResponse = callBackResponseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          callBackResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        switchResponse.failResponse(errorInfo.get());
        return switchResponse;
      }
      parseSuccessResult(callBackResponse, switchResponse);
      switchResponse.setServiceNote(urlE.getCallBackServiceName());
      return switchResponse;
    }
    switchResponse.failResponse(genConnectionErrorInfo(url));
    return switchResponse;
  }

  @Override
  public void parseSuccessResult(YunDingCallBackResponse yunDingCallBackResponse,
      EMeterSwitchResponse EMeterSwitchResponse) {
    String requestTime = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    String serviceId = yunDingCallBackResponse.getServiceId();
    log.info("设备闸门操作：{}，设备ID：{}，服务ID：{}，发起请求时间：{}", switchTypeE.getSwitchType(),
        EMeterSwitchResponse.getDeviceId(), serviceId, requestTime);
    EMeterSwitchResponse.successSwitchResponse(serviceId, requestTime, switchTypeE);
  }
}
