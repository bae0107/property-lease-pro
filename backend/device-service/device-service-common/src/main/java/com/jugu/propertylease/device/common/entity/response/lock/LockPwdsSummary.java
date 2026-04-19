package com.jugu.propertylease.device.common.entity.response.lock;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LockPwdsSummary extends DeviceResponse {

  private Map<String, PwdInfo> pwdInfos;

  public void successLockCheckResponse(Map<String, PwdInfo> pwdInfos) {
    super.setSuccess(true);
    this.pwdInfos = pwdInfos;
  }

  @Getter
  @Setter
  @ToString
  public static class PwdInfo {

    private String id;

    private long createdTimeMs;

    private int state;

    private boolean hasPermissionPeriod;

    private long permissionBeginSec;

    private long permissionEndSec;
  }
}
