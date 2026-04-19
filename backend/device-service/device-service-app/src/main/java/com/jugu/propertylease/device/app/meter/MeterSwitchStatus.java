package com.jugu.propertylease.device.app.meter;

import lombok.Getter;

@Getter
public enum MeterSwitchStatus {
  CLOSED(1),
  OPENED(2),
  CLOSING(3),
  OPENING(4),
  ABNORMAL(-1);

  private final int index;

  MeterSwitchStatus(int index) {
    this.index = index;
  }

  public static boolean isIndexValid(int index) {
    for (MeterSwitchStatus status : MeterSwitchStatus.values()) {
      if (status.getIndex() == index) {
        return true;
      }
    }
    return false;
  }
}
