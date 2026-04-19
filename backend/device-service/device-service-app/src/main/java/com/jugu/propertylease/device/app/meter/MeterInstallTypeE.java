package com.jugu.propertylease.device.app.meter;

import lombok.Getter;

@Getter
public enum MeterInstallTypeE {
  ALONE(1),
  AVERAGE(2);

  private final int index;

  MeterInstallTypeE(int index) {
    this.index = index;
  }

  public static boolean isIndexValid(int index) {
    for (MeterInstallTypeE typeE : MeterInstallTypeE.values()) {
      if (typeE.getIndex() == index) {
        return true;
      }
    }
    return false;
  }
}
