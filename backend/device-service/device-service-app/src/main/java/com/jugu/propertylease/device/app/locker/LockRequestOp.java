package com.jugu.propertylease.device.app.locker;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.device.app.entity.response.lock.LockCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock.YunDingAddPwdHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock.YunDingLockCheckHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock.YunDingLockPwdOpHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.lock.YunDingPwdInfosHandler;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum LockRequestOp implements LockI {
  YUN_DING {
    @Override
    public LockCheckResponse findLockInfo(String deviceId) {
      return new YunDingLockCheckHandler(deviceId).submitRequest();
    }

    @Override
    public AddLockPwdResponse addLockPwd(AddLockPwdRequest addLockPwdRequest) {
      return new YunDingAddPwdHandler(addLockPwdRequest).submitRequest();
    }

    @Override
    public LockPwdsSummary checkLockPwdInfos(String deviceId) {
      return new YunDingPwdInfosHandler(deviceId).submitRequest();
    }

    @Override
    public DeviceResponse delLockPwd(String deviceId, String pwdId) {
      return runLockOp(pwdId, deviceId, YunDingUrlE.DEL_LOCK_PWD);
    }

    @Override
    public DeviceResponse frozenLockPwd(String deviceId, String pwdId) {
      return runLockOp(pwdId, deviceId, YunDingUrlE.FROZEN_LOCK_PWD);
    }

    @Override
    public DeviceResponse unfrozenLockPwd(String deviceId, String pwdId) {
      return runLockOp(pwdId, deviceId, YunDingUrlE.UNFROZEN_LOCK_PWD);
    }

    private DeviceResponse runLockOp(String pwdId, String deviceId, YunDingUrlE yunDingUrlE) {
      try {
        int pwd = Integer.parseInt(pwdId);
        return new YunDingLockPwdOpHandler(deviceId, pwd, yunDingUrlE).submitRequest();
      } catch (NumberFormatException e) {
        log.error("yun ding pwd id:{} format error!", pwdId);
        DeviceResponse response = new DeviceResponse();
        ErrorType errorType = ErrorType.INPUT_ERROR;
        response.failResponse(new ErrorInfo(errorType.getErrorCode(), errorType.getErrorMsg()));
        return response;
      }
    }
  },

  HE_YI {
    @Override
    public LockCheckResponse findLockInfo(String deviceId) {
      return null;
    }

    @Override
    public AddLockPwdResponse addLockPwd(AddLockPwdRequest addLockPwdRequest) {
      return null;
    }

    @Override
    public LockPwdsSummary checkLockPwdInfos(String deviceId) {
      return null;
    }

    @Override
    public DeviceResponse delLockPwd(String deviceId, String pwdId) {
      return null;
    }

    @Override
    public DeviceResponse frozenLockPwd(String deviceId, String pwdId) {
      return null;
    }

    @Override
    public DeviceResponse unfrozenLockPwd(String deviceId, String pwdId) {
      return null;
    }
  }
}
