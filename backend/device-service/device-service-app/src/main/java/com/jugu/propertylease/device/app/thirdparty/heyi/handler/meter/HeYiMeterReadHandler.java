package com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter;

import com.google.gson.reflect.TypeToken;
import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Encodes;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiUrlE;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.error.HeYiErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.HeYiRequestHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.TokenException;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.lang.reflect.Type;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Log4j2
public abstract class HeYiMeterReadHandler<T extends DeviceResponse> extends
    HeYiRequestHandler<T, HeYiMeterCheckResponse> {

  private final HeYiUrlE heYiUrlE;

  private final String deviceId;

  public HeYiMeterReadHandler(String deviceId) {
    super(HeYiRequestHandler.FORM);
    this.heYiUrlE = HeYiUrlE.READ_METER;
    this.deviceId = deviceId;
  }

  @Override
  public T handleRequest(String domain, Sender<HeYiResponse<HeYiMeterCheckResponse>> sender,
      String serviceName) throws TokenException {
    String url = heYiUrlE.getUrl();
    String formedUrl = domain + url;
    Type type = new TypeToken<HeYiResponse<HeYiMeterCheckResponse>>() {
    }.getType();
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("meterNo", deviceId);
    Optional<HeYiResponse<HeYiMeterCheckResponse>> responseOptional = sender.sendPostRequest(
        formedUrl, Encodes.toFormUrlEncoder(map), type);
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
