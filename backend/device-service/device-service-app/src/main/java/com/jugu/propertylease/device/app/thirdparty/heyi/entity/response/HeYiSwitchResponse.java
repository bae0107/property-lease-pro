package com.jugu.propertylease.device.app.thirdparty.heyi.entity.response;

import com.jugu.propertylease.common.utils.Common;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class HeYiSwitchResponse {

  private String type;

  private String result;

  private String action;

  private String meterNo;

  private String valveNo;

  private String valveStatus;

  public boolean isSuccess() {
    return !Common.isStringInValid(this.result) && "success".equals(
        this.result.toLowerCase(Locale.ROOT).trim());
  }
}
