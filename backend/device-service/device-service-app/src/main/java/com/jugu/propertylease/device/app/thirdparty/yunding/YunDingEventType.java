package com.jugu.propertylease.device.app.thirdparty.yunding;

import com.jugu.propertylease.device.app.task.EventTaskI;
import lombok.Getter;

@Getter
public enum YunDingEventType implements EventTaskI<YunDingEventDTO, YunDingEventMgr> {
  batteryAlarm("门锁低电量事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processCommonLockEvent(event, YunDingEventType.batteryAlarm);
    }
  },

  clearBattryAlarm("门锁解除低电量事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processCommonLockEvent(event, YunDingEventType.clearBattryAlarm);
    }
  },

  brokenAlarm("门锁被破坏事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processCommonLockEvent(event, YunDingEventType.brokenAlarm);
    }
  },

  wrongPwdAlarm("密码连续输入错误事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processCommonLockEvent(event, YunDingEventType.wrongPwdAlarm);
    }
  },

  lockerOpenAlarm("开锁事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  lockOfflineAlarm("设备离线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processOfflineEvent(event);
    }
  },

  clearLockOfflineAlarm("设备解除离线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processOnlineEvent(event);
    }
  },

  centerOfflineAlarm("网关离线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  clearCenterOfflineAlarm("网关解除离线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  batteryAsync("门锁电量更新事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processLockElectricitySyncEvent(event);
    }
  },

  installSubmit("房源设备安装完成事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  deviceInstall("设备绑定事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  deviceUninstall("设备解绑事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  elemeterPowerAsync("电表电量同步事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processEMeterPowerAsyncEvent(event);
    }
  },

  waterGatewayOfflineAlarm("水表采集器离线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  waterGatewayOnlineAlarm("水表采集器在线事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {

    }
  },

  watermeterAmountAsync("水表读数更新事件") {
    @Override
    public void run(YunDingEventDTO event, YunDingEventMgr yunDingEventMgr) {
      yunDingEventMgr.processWMeterPowerAsyncEvent(event);
    }
  };

  private final String name;

  YunDingEventType(String name) {
    this.name = name;
  }
}
