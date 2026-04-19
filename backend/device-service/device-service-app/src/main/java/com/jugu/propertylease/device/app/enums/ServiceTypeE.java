package com.jugu.propertylease.device.app.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ServiceTypeE {
  OPEN(1),
  CLOSE(2),
  RECORD(3),
  ADD(4),
  DEL(5),
  FROZEN(6),
  UNFROZEN(7);

  private final int index;
}
