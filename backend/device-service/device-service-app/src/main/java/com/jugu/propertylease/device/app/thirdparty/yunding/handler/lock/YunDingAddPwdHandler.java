package com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.request.YunDingAddLockPwdRequest;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingAddLockPwdResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class YunDingAddPwdHandler extends
    YunDingRequestHandler<AddLockPwdResponse, YunDingAddLockPwdResponse> {

  private final AddLockPwdRequest lockPwdRequest;

  @Override
  public AddLockPwdResponse handleTokenError() {
    AddLockPwdResponse pwdResponse = new AddLockPwdResponse();
    pwdResponse.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return pwdResponse;
  }

  @Override
  public AddLockPwdResponse handleRequest(String token, String domain,
      Sender<YunDingAddLockPwdResponse> sender, String serviceName) {
    YunDingAddLockPwdRequest pwdRequest = new YunDingAddLockPwdRequest(lockPwdRequest, token);
    String requestJson = GsonFactory.toJson(pwdRequest);
    YunDingUrlE urlE = YunDingUrlE.ADD_LOCK_PWD;
    String url = urlE.getUrl();
    Optional<YunDingAddLockPwdResponse> responseOptional = sender.sendPostRequest(domain + url,
        requestJson, YunDingAddLockPwdResponse.class);
    AddLockPwdResponse addLockPwdResponse = new AddLockPwdResponse();
    addLockPwdResponse.setDeviceId(lockPwdRequest.getDeviceId());
    if (responseOptional.isPresent()) {
      YunDingAddLockPwdResponse pwdResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(pwdResponse,
          serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        addLockPwdResponse.failResponse(errorInfo.get());
        return addLockPwdResponse;
      }
      parseSuccessResult(pwdResponse, addLockPwdResponse);
      addLockPwdResponse.setServiceNote(urlE.getCallBackServiceName());
      return addLockPwdResponse;
    }
    addLockPwdResponse.failResponse(genConnectionErrorInfo(url));
    return addLockPwdResponse;
  }

  @Override
  public void parseSuccessResult(YunDingAddLockPwdResponse yunDingResponse,
      AddLockPwdResponse addLockPwdResponse) {
    addLockPwdResponse.successAddLockPwd(String.valueOf(yunDingResponse.getPasswordId()),
        yunDingResponse.getServiceId());
  }
}
