package com.jugu.propertylease.device.common.entity.response.lock;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AddLockPwdResponse extends DeviceResponse {

  private String passwordId;

  public void successAddLockPwd(String pwdId, String serviceId) {
    super.setSuccess(true);
    super.setServiceId(serviceId);
    this.passwordId = pwdId;
  }
}
