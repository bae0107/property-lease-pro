package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock;

import com.jugu.propertylease.device.app.locker.LockPwdStateE;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum YunDingPwdStateE {
  INIT(1, LockPwdStateE.INIT),
  VALID(2, LockPwdStateE.VALID),
  VALID_FUTURE(3, LockPwdStateE.VALID_FUTURE),
  EXPIRED(4, LockPwdStateE.EXPIRED),
  FROZEN(5, LockPwdStateE.FROZEN);

  private static final Map<Integer, LockPwdStateE> pwdStateEMap = new HashMap<>();

  static {
    for (YunDingPwdStateE stateE : YunDingPwdStateE.values()) {
      pwdStateEMap.put(stateE.stateIndex, stateE.commPwdStateE);
    }
  }

  private final int stateIndex;
  private final LockPwdStateE commPwdStateE;

  public static LockPwdStateE findCommonState(int state) {
    return pwdStateEMap.getOrDefault(state, LockPwdStateE.UNKNOWN);
  }
}
