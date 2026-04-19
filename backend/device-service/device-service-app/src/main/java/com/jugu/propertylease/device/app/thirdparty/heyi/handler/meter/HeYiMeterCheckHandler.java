package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.google.gson.reflect.TypeToken;
import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiUrlE;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.error.HeYiErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.HeYiRequestHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.TokenException;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.lang.reflect.Type;
import java.util.Optional;

public abstract class HeYiMeterCheckHandler<T extends DeviceResponse> extends
    HeYiRequestHandler<T, HeYiMeterCheckResponse> {

  private final HeYiUrlE heYiUrlE;

  private final String deviceId;

  public HeYiMeterCheckHandler(String deviceId) {
    super(HeYiRequestHandler.JSON);
    this.heYiUrlE = HeYiUrlE.CHECK_METER;
    this.deviceId = deviceId;
  }

  @Override
  public T handleRequest(String domain, Sender<HeYiResponse<HeYiMeterCheckResponse>> sender,
      String serviceName) throws TokenException {
    String url = String.format(heYiUrlE.getUrl(), deviceId);
    String formedUrl = domain + url;
    Type type = new TypeToken<HeYiResponse<HeYiMeterCheckResponse>>() {
    }.getType();
    Optional<HeYiResponse<HeYiMeterCheckResponse>> responseOptional = sender.sendGetRequest(
        formedUrl, type);
    if (responseOptional.isPresent()) {
      HeYiResponse<HeYiMeterCheckResponse> heYiResponse = responseOptional.get();
      HeYiErrorMsgInfo errorMsgInfo = HeYiErrorMsgInfo.findMsgInfo(heYiResponse);
      HeYiMeterCheckResponse readResponse = heYiResponse.getObject();
      if (errorMsgInfo == HeYiErrorMsgInfo.SUCCESS && readResponse != null
          && readResponse.isSuccess()) {
        return parseSuccessResult(readResponse);
      } else {
        ErrorInfo errorInfo = new ErrorInfo(-1, heYiResponse.getMessage());
        return parseFailResult(errorInfo);
      }
    }
    return handleRequestError(sender.getException(), url);
  }
}
