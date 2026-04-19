package com.jugu.propertylease.device.app.thirdparty.yunding;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.LockInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.app.locker.LockService;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.EventInfoRepository;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity.YunDingEMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock.YunDingLockCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.water.YunDingWMeterCheckResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
@Log4j2
public class YunDingEventMgr {

  private static final ProviderOpE opE = ProviderOpE.YUN_DING;
  private static final int ONLINE = 1;
  private static final int OFFLINE = 2;
  private final ElectronicMeterService electronicMeterService;
  private final WaterMeterService waterMeterService;
  private final LockService lockService;
  private final EventInfoRepository eventInfoRepository;

  @Transactional
  public void processEMeterPowerAsyncEvent(YunDingEventDTO event) {
    String uuid = event.getUuid();
    String detail = event.getDetail();
    if (Common.isStringInValid(uuid) || Common.isStringInValid(detail)) {
      log.error("yun ding e meter async event error, uuid or detail null");
      return;
    }
    YunDingEMeterCheckResponse yunDingEMeterCheckResponse = GsonFactory.fromJson(detail,
        YunDingEMeterCheckResponse.class);
    double consume = yunDingEMeterCheckResponse.getConsumeAmount();
    long time = yunDingEMeterCheckResponse.getRecordTime();
    try {
      Optional<ElectronicMeterInfo> meterInfoOptional = electronicMeterService.findEMeterInfoByDeviceIdAndProvider(
          uuid, opE.getIndex(), true);
      if (meterInfoOptional.isPresent()) {
        if (!electronicMeterService.updateMeterConsumeInfo(meterInfoOptional.get(), consume,
            Common.findTimeByMillSecondTimestamp(time),
            opE.getName(), YunDingEventType.elemeterPowerAsync.getName())) {
          log.error("yun ding e meter async event error, due to time or consume abnormal!");
        }
        return;
      }
      log.error("yun ding e meter async event error, due to info miss for uuid: {}, provider: {}",
          uuid, opE);
    } catch (Exception e) {
      log.error("yun ding e meter async event error due to db error: {}", e.getMessage());
    }
  }

  @Transactional
  public void processWMeterPowerAsyncEvent(YunDingEventDTO event) {
    String uuid = event.getUuid();
    String detail = event.getDetail();
    if (Common.isStringInValid(uuid) || Common.isStringInValid(detail)) {
      log.error("yun ding w meter async event error, uuid or detail null");
      return;
    }
    YunDingWMeterCheckResponse.Info info = GsonFactory.fromJson(detail,
        YunDingWMeterCheckResponse.Info.class);
    double consume = info.getAmount();
    long time = event.getTime();
    try {
      Optional<WaterMeterInfo> meterInfoOptional = waterMeterService.findWMeterInfoByDeviceIdAndProvider(
          uuid, opE.getIndex(), true);
      if (meterInfoOptional.isPresent()) {
        if (!waterMeterService.getWaterMeterProcessor()
            .updateMeterConsumeInfo(meterInfoOptional.get(), consume,
                Common.findTimeByMillSecondTimestamp(time),
                opE.getName(), YunDingEventType.watermeterAmountAsync.getName())) {
          log.error("yun ding w meter async event error, due to time or consume abnormal!");
        }
        return;
      }
      log.error("yun ding w meter async event error, due to info miss for uuid: {}, provider: {}",
          uuid, opE);
    } catch (Exception e) {
      log.error("yun ding w meter async event error due to db error: {}", e.getMessage());
    }
  }

  public void processLockElectricitySyncEvent(YunDingEventDTO event) {
    String uuid = event.getUuid();
    String detail = event.getDetail();
    if (Common.isStringInValid(uuid) || Common.isStringInValid(detail)) {
      log.error("yun ding lock electricity sync event error, uuid or detail null");
      return;
    }
    YunDingLockCheckResponse lockInfo = GsonFactory.fromJson(detail,
        YunDingLockCheckResponse.class);
    try {
      lockService.syncLockElectricity(uuid, opE.getIndex(), lockInfo.getElectricity(),
          opE.getName(), YunDingEventType.batteryAsync.getName());
    } catch (Exception e) {
      log.error("yun ding electricity sync event error due to db error: {}", e.getMessage());
    }
  }

  public void processOnlineEvent(YunDingEventDTO event) {
    processOnOffLineEvent(event, ONLINE, YunDingEventType.clearLockOfflineAlarm);
  }

  public void processOfflineEvent(YunDingEventDTO event) {
    processOnOffLineEvent(event, OFFLINE, YunDingEventType.lockOfflineAlarm);
  }

  public void processCommonLockEvent(YunDingEventDTO event, YunDingEventType eventType) {
    String uuid = event.getUuid();
    if (Common.isStringInValid(uuid)) {
      log.error("yun ding common lock event error, uuid null");
      return;
    }
    try {
      Optional<LockInfo> infoOptional = lockService.findLockInfoByDeviceIdAndProvider(uuid,
          opE.getIndex());
      infoOptional.ifPresent(lockInfo -> addNewEventInfo(lockInfo.getId(),
          ThirdPartyServiceRecordMgr.DeviceType.LOCKER.getIndex(), eventType));
    } catch (Exception e) {
      log.error("yun ding common lock event error due to db error: {}", e.getMessage());
    }
  }

  private void addNewEventInfo(long deviceKey, int deviceType, YunDingEventType eventType) {
    eventInfoRepository.addNewEventInfo(deviceKey, opE.getIndex(), deviceType, eventType.name(),
        eventType.getName());
  }

  private void processOnOffLineEvent(YunDingEventDTO event, int status,
      YunDingEventType eventType) {
    String uuid = event.getUuid();
    if (Common.isStringInValid(uuid)) {
      log.error("yun ding device on off line event error, uuid null");
      return;
    }
    String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    int opIndex = opE.getIndex();
    String opName = opE.getName();
    String name = eventType.getName();
    try {
      if (lockService.syncLockOnOffLine(status, time, opName, name, opIndex, uuid) > 0) {
        Optional<LockInfo> infoOptional = lockService.findLockInfoByDeviceIdAndProvider(uuid,
            opIndex);
        infoOptional.ifPresent(lockInfo -> addNewEventInfo(lockInfo.getId(),
            ThirdPartyServiceRecordMgr.DeviceType.LOCKER.getIndex(), eventType));
        return;
      }
      if (waterMeterService.syncWMeterOnOffLine(status, time, opName, name, opIndex, uuid) > 0) {
        Optional<WaterMeterInfo> infoOptional = waterMeterService.findWMeterInfoByDeviceIdAndProvider(
            uuid, opIndex, false);
        infoOptional.ifPresent(wInfo -> addNewEventInfo(wInfo.getId(),
            ThirdPartyServiceRecordMgr.DeviceType.WATER.getIndex(), eventType));
        return;
      }
      if (electronicMeterService.syncEMeterOnOffLine(status, time, opName, name, opIndex, uuid)
          > 0) {
        Optional<ElectronicMeterInfo> infoOptional = electronicMeterService.findEMeterInfoByDeviceIdAndProvider(
            uuid, opIndex, false);
        infoOptional.ifPresent(eInfo -> addNewEventInfo(eInfo.getId(),
            ThirdPartyServiceRecordMgr.DeviceType.ELECTRICITY.getIndex(), eventType));
      }
    } catch (Exception e) {
      log.error("yun ding device on off line event error due to db error: {}", e.getMessage());
    }
  }
}
