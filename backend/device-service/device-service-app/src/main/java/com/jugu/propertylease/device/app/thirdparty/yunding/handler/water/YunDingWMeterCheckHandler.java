package com.jugu.propertylease.device.app.thirdparty.yunding.handler.water;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.water.YunDingWMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class YunDingWMeterCheckHandler extends
    YunDingRequestHandler<WMeterCheckResponse, YunDingWMeterCheckResponse> {

  private final String deviceId;

  @Override
  public WMeterCheckResponse handleTokenError() {
    WMeterCheckResponse checkResponse = new WMeterCheckResponse();
    checkResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return checkResponse;
  }

  @Override
  public WMeterCheckResponse handleRequest(String token, String domain,
      Sender<YunDingWMeterCheckResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.WATER_METER_INFO;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId);
    Optional<YunDingWMeterCheckResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingWMeterCheckResponse.class);
    WMeterCheckResponse checkResponse = new WMeterCheckResponse();
    checkResponse.setDeviceId(deviceId);
    if (responseOptional.isPresent()) {
      YunDingWMeterCheckResponse wMeterCheckResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(
          wMeterCheckResponse, serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        checkResponse.failResponse(errorInfo.get());
        return checkResponse;
      }
      YunDingWMeterCheckResponse.Info info = wMeterCheckResponse.getInfo();
      if (info == null) {
        ErrorTypesE errorType = ErrorTypesE.THIRD_PARTY_ERROR;
        log.error("yun ding check water meter failed due to info pojo invalid! DeviceId: {}",
            deviceId);
        checkResponse.failResponse(
            new ErrorInfo(errorType.getErrorCode(), String.format(errorType.getErrorMsg(),
                serviceName, urlE.getName(), "第三方无识别", "info无效")));
        return checkResponse;
      }
      String recordTime = info.getRecordTime();
      String onOffTime = info.getOnOffTime();
      if (Common.isStringInValid(recordTime) || Common.isStringInValid(onOffTime)) {
        ErrorTypesE errorType = ErrorTypesE.THIRD_PARTY_ERROR;
        log.error("yun ding check water meter failed due to time param invalid! DeviceId: {}",
            deviceId);
        checkResponse.failResponse(
            new ErrorInfo(errorType.getErrorCode(), String.format(errorType.getErrorMsg(),
                serviceName, urlE.getName(), "第三方无识别", "查询时间字段无效")));
        return checkResponse;
      }
      parseSuccessResult(wMeterCheckResponse, checkResponse);
      return checkResponse;
    }
    checkResponse.failResponse(genConnectionErrorInfo(url));
    return checkResponse;
  }

  @Override
  public void parseSuccessResult(YunDingWMeterCheckResponse yunDingResponse,
      WMeterCheckResponse meterResponse) {
    YunDingWMeterCheckResponse.Info info = yunDingResponse.getInfo();
    meterResponse.successFullInfoResponse(info.getAmount(),
        Common.findTimeSecondByUTC(info.getRecordTime()), info.getOnOff(),
        Common.findTimeSecondByUTC(info.getOnOffTime()), info.getMeterType(), info.getOnOffLine());
  }
}
