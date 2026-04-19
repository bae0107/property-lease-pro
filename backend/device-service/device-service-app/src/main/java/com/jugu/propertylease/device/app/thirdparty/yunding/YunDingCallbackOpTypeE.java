package com.jugu.propertylease.device.app.thirdparty.yunding;

import lombok.Getter;

@Getter
public enum YunDingCallbackOpTypeE {
  OPEN_SWITCH(7),
  CLOSE_SWITCH(6);

  private final int index;

  YunDingCallbackOpTypeE(int index) {
    this.index = index;
  }
}
