package com.jugu.propertylease.device.app.locker;

import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;

public interface LockPwdOpI {

  DeviceResponse runOp(String deviceId, String pwdId, ProviderOpE providerOpE);
}
