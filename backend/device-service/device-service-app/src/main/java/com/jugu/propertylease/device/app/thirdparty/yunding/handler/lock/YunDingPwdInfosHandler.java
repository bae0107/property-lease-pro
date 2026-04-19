package com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingPwdCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingPwdInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingPwdStateE;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.YunDingRequestHandler;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class YunDingPwdInfosHandler extends
    YunDingRequestHandler<LockPwdsSummary, YunDingPwdCheckResponse> {

  private final String deviceId;

  @Override
  public LockPwdsSummary handleTokenError() {
    LockPwdsSummary pwdsSummary = new LockPwdsSummary();
    pwdsSummary.failResponse(genConnectionErrorInfo(YunDingUrlE.ACCESS_TOKEN.getUrl()));
    return pwdsSummary;
  }

  @Override
  public LockPwdsSummary handleRequest(String token, String domain,
      Sender<YunDingPwdCheckResponse> sender, String serviceName) {
    YunDingUrlE urlE = YunDingUrlE.GET_PWD_INFOS;
    String url = urlE.getUrl();
    String formedUrl = domain + String.format(url, token, deviceId);
    Optional<YunDingPwdCheckResponse> responseOptional = sender.sendGetRequest(formedUrl,
        YunDingPwdCheckResponse.class);
    LockPwdsSummary lockPwdsSummary = new LockPwdsSummary();
    lockPwdsSummary.setDeviceId(deviceId);
    if (responseOptional.isPresent()) {
      YunDingPwdCheckResponse checkResponse = responseOptional.get();
      Optional<ErrorInfo> errorInfo = YunDingErrorMsgInfo.syncErrorInfoByYunDingError(checkResponse,
          serviceName, urlE.getName());
      if (errorInfo.isPresent()) {
        lockPwdsSummary.failResponse(errorInfo.get());
        return lockPwdsSummary;
      }
      parseSuccessResult(checkResponse, lockPwdsSummary);
      return lockPwdsSummary;
    }
    lockPwdsSummary.failResponse(genConnectionErrorInfo(url));
    return lockPwdsSummary;
  }

  @Override
  public void parseSuccessResult(YunDingPwdCheckResponse yunDingResponse,
      LockPwdsSummary lockPwdsSummary) {
    Map<String, YunDingPwdInfo> yunDingPwdInfos = yunDingResponse.getYunDingPwdInfos();
    if (yunDingPwdInfos == null || yunDingPwdInfos.isEmpty()) {
      lockPwdsSummary.successLockCheckResponse(new HashMap<>());
      return;
    }
    Map<String, LockPwdsSummary.PwdInfo> pwdInfos = new HashMap<>();
    yunDingPwdInfos.forEach((id, info) -> {
      LockPwdsSummary.PwdInfo pwdInfo = new LockPwdsSummary.PwdInfo();
      pwdInfo.setId(id);
      pwdInfo.setCreatedTimeMs(info.getTime());
      YunDingPwdInfo.Permission permission = info.getPermission();
      if (permission != null) {
        int permissionState = permission.getStatus();
        if (permissionState == 2) {
          pwdInfo.setPermissionBeginSec(permission.getBegin());
          pwdInfo.setPermissionEndSec(permission.getEnd());
          pwdInfo.setHasPermissionPeriod(true);
        } else {
          pwdInfo.setHasPermissionPeriod(false);
        }
      } else {
        pwdInfo.setHasPermissionPeriod(false);
      }
      pwdInfo.setState(YunDingPwdStateE.findCommonState(info.getPwdState()).getState());
      pwdInfos.put(id, pwdInfo);
    });
    lockPwdsSummary.successLockCheckResponse(pwdInfos);
  }
}
