package com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.lock.LockCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingLockCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class YunDingLockCheckHandler extends
    YunDingRequestHandler<LockCheckResponse, YunDingLockCheckResponse> {

  private final String deviceId;

  @Override
  public LockCheckResponse handleTokenError() {
    LockCheckResponse checkResponse = new LockCheckResponse();
    checkResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return checkResponse;
  }

  @Override
  public LockCheckResponse handleRequest(String token, String domain,
      Sender<YunDingLockCheckResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.GET_LOCK_INFO;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId);
    Optional<YunDingLockCheckResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingLockCheckResponse.class);
    LockCheckResponse lockCheckResponse = new LockCheckResponse();
    lockCheckResponse.setDeviceId(deviceId);
    if (responseOptional.isPresent()) {
      YunDingLockCheckResponse checkResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(checkResponse,
          serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        lockCheckResponse.failResponse(errorInfo.get());
        return lockCheckResponse;
      }
      parseSuccessResult(checkResponse, lockCheckResponse);
      return lockCheckResponse;
    }
    lockCheckResponse.failResponse(genConnectionErrorInfo(url));
    return lockCheckResponse;
  }

  @Override
  public void parseSuccessResult(YunDingLockCheckResponse yunDingResponse,
      LockCheckResponse checkResponse) {
    checkResponse.successLockCheckResponse(yunDingResponse.getName(), yunDingResponse.getMac(),
        yunDingResponse.getSn(),
        yunDingResponse.getModel(), yunDingResponse.getOnOffLine(),
        Common.findTimeBySecondTimestamp(yunDingResponse.getOnOffTime()),
        yunDingResponse.getLockSignal(), yunDingResponse.getElectricity());
  }
}
