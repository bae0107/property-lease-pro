package com.jugu.propertylease.device.app.locker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LockPwdStateE {
  INIT(1),
  VALID(2),
  VALID_FUTURE(3),
  EXPIRED(4),
  FROZEN(5),
  UNKNOWN(-1);

  private final int state;
}
