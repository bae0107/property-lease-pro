package com.jugu.propertylease.device.common.entity.request;

import com.jugu.propertylease.common.utils.Common;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "添加智能锁密码请求")
public class AddLockPwdRequest {

  @Schema(description = "设备deviceId")
  private String deviceId;

  @Schema(description = "设备供应商编号")
  private int providerOp;

  @Schema(description = "通知人手机号")
  private String phone;

  @Schema(description = "预设密码6位数")
  private String password;

  @Schema(description = "密码名称")
  private String name;

  @Schema(description = "密码有效开始时间，秒级时间戳")
  private long begin;

  @Schema(description = "密码有效结束时间，秒级时间戳")
  private long end;

  public boolean isValid() {
    if (!Common.isStringInValid(deviceId) && !Common.isStringInValid(phone)
        && !Common.isStringInValid(password)
        && !Common.isStringInValid(name) && password.matches("^\\d{6}$") && phone.matches(
        "^1\\d{10}$")) {
      String curTime = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
      String st = Common.findTimeByMillSecondTimestamp(begin * 1000L);
      String ed = Common.findTimeByMillSecondTimestamp(end * 1000L);
      return st.compareTo(ed) < 0 && curTime.compareTo(ed) < 0;
    }
    return false;
  }
}
