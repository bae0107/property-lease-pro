package com.jugu.propertylease.device.app.schedule;

import lombok.Data;

@Data
public class MeterScheduleRecordDetail {

  private long tempId;

  /**
   * 设备内部主键
   */
  private long id;

  private int result;

  private String errorInfo;
}
