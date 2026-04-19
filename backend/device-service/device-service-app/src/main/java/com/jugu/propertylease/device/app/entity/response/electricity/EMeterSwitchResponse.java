package com.jugu.propertylease.device.app.entity.response.electricity;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EMeterSwitchResponse extends DeviceResponse {

  /**
   * 开关请求发起时间，年月日时分秒
   */
  private String requestTime;

  private int switchType;

  public void successSwitchResponse(String serviceId, String requestTime, SwitchTypeE switchTypeE) {
    super.setSuccess(true);
    super.setServiceId(serviceId);
    this.requestTime = requestTime;
    this.switchType = switchTypeE.switchType;
  }

  @Getter
  public enum SwitchTypeE {
    OPEN(1),
    CLOSE(2);

    private final int switchType;

    SwitchTypeE(int switchType) {
      this.switchType = switchType;
    }

    public static Optional<SwitchTypeE> findTypeByIndex(int index) {
      for (SwitchTypeE typeE : SwitchTypeE.values()) {
        if (index == typeE.getSwitchType()) {
          return Optional.of(typeE);
        }
      }
      return Optional.empty();
    }
  }
}
