package com.jugu.propertylease.device.app.locker;

import com.jugu.propertylease.device.app.entity.response.lock.LockCheckResponse;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;

public interface LockI {

  LockCheckResponse findLockInfo(String deviceId);

  AddLockPwdResponse addLockPwd(AddLockPwdRequest addLockPwdRequest);

  LockPwdsSummary checkLockPwdInfos(String deviceId);

  DeviceResponse delLockPwd(String deviceId, String pwdId);

  DeviceResponse frozenLockPwd(String deviceId, String pwdId);

  DeviceResponse unfrozenLockPwd(String deviceId, String pwdId);
}
