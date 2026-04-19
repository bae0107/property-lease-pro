package com.jugu.propertylease.common.info;

import lombok.Getter;

@Getter
public enum ErrorType {
  DB_ERROR(1, "数据库错误"),

  INPUT_ERROR(2, "请求中含非法或无效参数"),

  SERIOUS_ERROR(3, "严重错误：%s"),

  DATA_ABNORMAL_ERROR(4, "数据库信息不对称：%s"),

  SYSTEM_ERROR(5, "系统异常：%s"),

  OUT_ERROR(6, "外部错误：%s"),

  SYSTEM_BUSY(7, "系统繁忙，请稍后重试"),

  TIME_OUT(5, "请求超时：%s"),

  AUTH_ERROR(6, "登录无效");

  private final int errorCode;

  private final String errorMsg;

  ErrorType(int errorCode, String errorMsg) {
    this.errorCode = errorCode;
    this.errorMsg = errorMsg;
  }
}
