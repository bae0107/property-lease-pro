package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.google.gson.reflect.TypeToken;
import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.Encodes;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiUrlE;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiSwitchResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.error.HeYiErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.HeYiRequestHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.TokenException;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import java.lang.reflect.Type;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Log4j2
public class HeYiEMeterSwitchHandler extends
    HeYiRequestHandler<EMeterCheckResponse, HeYiSwitchResponse> {

  private final String deviceId;

  private final String requestAction;

  private final EMeterSwitchResponse.SwitchTypeE switchTypeE;

  public HeYiEMeterSwitchHandler(String deviceId, EMeterSwitchResponse.SwitchTypeE switchTypeE) {
    super(HeYiRequestHandler.FORM);
    this.deviceId = deviceId;
    this.requestAction = switchTypeE == EMeterSwitchResponse.SwitchTypeE.OPEN ? "open" : "close";
    this.switchTypeE = switchTypeE;
  }

  @Override
  public EMeterCheckResponse parseFailResult(ErrorInfo errorInfo) {
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    checkResponse.failResponse(errorInfo);
    return checkResponse;
  }

  @Override
  public EMeterCheckResponse handleRequest(String domain,
      Sender<HeYiResponse<HeYiSwitchResponse>> sender, String serviceName) throws TokenException {
    HeYiUrlE urlE = HeYiUrlE.ELE_SWITCH;
    String url = urlE.getUrl();
    String formedUrl = domain + urlE.getUrl();
    Type type = new TypeToken<HeYiResponse<HeYiSwitchResponse>>() {
    }.getType();
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("meterNo", deviceId);
    map.add("action", requestAction);
    Optional<HeYiResponse<HeYiSwitchResponse>> responseOptional = sender.sendPostRequest(formedUrl,
        Encodes.toFormUrlEncoder(map), type);
    if (responseOptional.isPresent()) {
      HeYiResponse<HeYiSwitchResponse> heYiResponse = responseOptional.get();
      HeYiErrorMsgInfo errorMsgInfo = HeYiErrorMsgInfo.findMsgInfo(heYiResponse);
      HeYiSwitchResponse switchResponse = heYiResponse.getObject();
      if (errorMsgInfo == HeYiErrorMsgInfo.SUCCESS && switchResponse != null
          && switchResponse.isSuccess()) {
        return parseSuccessResult(switchResponse);
      } else {
        ErrorInfo errorInfo = new ErrorInfo(-1, heYiResponse.getMessage());
        return parseFailResult(errorInfo);
      }
    }
    return handleRequestError(sender.getException(), url);
  }

  @Override
  public EMeterCheckResponse parseSuccessResult(HeYiSwitchResponse responseObject) {
    EMeterCheckResponse checkResponse = new EMeterCheckResponse();
    String action = responseObject.getAction();
    String meterNo = responseObject.getMeterNo();
    String status = responseObject.getValveStatus();
    if (Common.isStringInValid(action) || Common.isStringInValid(meterNo) || Common.isStringInValid(
        status)
        || !action.equals(status) || !meterNo.equals(deviceId) || !requestAction.equals(status)) {
      log.error(
          "he yi switch response param invalid: action:{}, meter:{}, status:{}, device:{}, requestAction:{}",
          action, meterNo, status, deviceId, requestAction);
      ErrorType type = ErrorType.OUT_ERROR;
      ErrorInfo errorInfo = new ErrorInfo(type.getErrorCode(),
          String.format(type.getErrorMsg(), "合一闸门控制返回参数错误！"));
      checkResponse.failResponse(errorInfo);
      return checkResponse;
    }
    String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    checkResponse.successCheckEnableResponse(switchTypeE.getSwitchType(), time);
    return checkResponse;
  }
}
