package com.jugu.propertylease.main.outer.ali;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum AliYunMsgOp implements AliYunMsgTools.Builder {
  VALID_CODE("SMS_485435229", "{'code':'%s'}") {
    @Override
    public String buildCt(String... params) {
      if (params.length != 1) {
        throw new IllegalArgumentException("wrong param nums");
      }
      return String.format(this.getContentFormat(), (Object[]) params);
    }
  },

  MULTI_VALUE_EXAMPLE("SMS_485500241",
      "{'orderId':'%s', 'time':'%s', 'amount': '%s', 'userNick': '%s'}") {
    @Override
    public String buildCt(String... params) {
      if (params.length != 4) {
        throw new IllegalArgumentException("wrong param nums");
      }
      return String.format(this.getContentFormat(), (Object[]) params);
    }
  };

  private final String tempId;

  private final String contentFormat;
}
