package com.jugu.propertylease.device.app.thirdparty.yunding;

import lombok.Getter;

@Getter
public enum YunDingUrlE {
  ACCESS_TOKEN("获取通行证", "/access_token", ""),
  READ_ELE_METER("获取电表信息", "/get_elemeter_info?access_token=%s&uuid=%s", ""),
  CHECK_ELE_PERIOD_USAGE("获取时间段内电表读数",
      "/elemeter_fetch_power_consumption?access_token=%s&uuid=%s&start_time=%s&end_time=%s", ""),
  ELE_SWITCH_ON("电表开闸", "/elemeter_switch_on", "Elemeter_Control_Service"),
  ELE_SWITCH_OFF("电表关闸", "/elemeter_switch_off", "Elemeter_Control_Service"),
  WATER_METER_INFO("获取水表信息", "/get_watermeter_info?access_token=%s&uuid=%s&manufactory=ym",
      ""),
  READ_WATER_METER("水表抄表发起", "/read_watermeter?access_token=%s&uuid=%s&manufactory=ym",
      "Watermeter_Read_Service"),
  READ_WATER_METER_STATUS("水表抄表进度",
      "/read_watermeter_status?access_token=%s&uuid=%s&manufactory=ym", ""),
  GET_LOCK_INFO("获取门锁信息", "/get_lock_info?access_token=%s&uuid=%s", ""),
  ADD_LOCK_PWD("添加门锁密码", "/add_password", "Password_Add_Service"),
  GET_PWD_INFOS("获取门锁密码信息", "/fetch_passwords?access_token=%s&uuid=%s", ""),
  DEL_LOCK_PWD("删除门锁密码", "/delete_password", "Password_Delete_Service"),
  FROZEN_LOCK_PWD("冻结门锁密码", "/frozen_password", "Password_Frozen_Service"),
  UNFROZEN_LOCK_PWD("解冻门锁密码", "/unfrozen_password", "Password_Unfrozen_Service");

  private final String name;

  private final String url;

  private final String callBackServiceName;

  YunDingUrlE(String name, String url, String callBackServiceName) {
    this.name = name;
    this.url = url;
    this.callBackServiceName = callBackServiceName;
  }
}
