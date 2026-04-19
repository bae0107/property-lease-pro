package com.jugu.propertylease.device.app.enums;

import lombok.Getter;

/**
 * 我方服务错误或第三方服务错误归纳
 */
@Getter
public enum ErrorTypesE {
  TEST_ERROR(10001, "测试数据"),
  CONNECTION_ERROR(4000, "向第三方:%s-发送请求时连接错误,链接请求:%s"),
  THIRD_PARTY_ERROR(4001, "向第三方:%s-发送请求:%s时报错,错误码:%d,错误信息:%s");

  private final int errorCode;

  private final String errorMsg;

  ErrorTypesE(int errorCode, String errorMsg) {
    this.errorCode = errorCode;
    this.errorMsg = errorMsg;
  }
}
