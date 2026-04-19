package com.jugu.propertylease.device.app.callback;

import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.enums.ServiceTypeE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
public class CallbackTaskAllocator {

  private final CallBackComponents callBackComponents;

  public void processCallbackTask(ProviderOpE providerOpE, String serviceId,
      CallBackTaskCallbackRunnerI backTaskCallbackRunnerI) {
    int providerIndex = providerOpE.getIndex();
    try {
      ThirdPartyServiceTemp serviceTemp = callBackComponents.getThirdPartyServiceRecordMgr()
          .findRecord(serviceId, providerOpE.getIndex());
      log.info(serviceTemp);
      if (serviceTemp != null) {
        int serviceStatus = serviceTemp.getServiceresult();
        if (serviceStatus == ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex()) {
          int deviceTypeIndex = serviceTemp.getDevicetype();
          Optional<ThirdPartyServiceRecordMgr.DeviceType> deviceType = ThirdPartyServiceRecordMgr.DeviceType.findTypeByIndex(
              deviceTypeIndex);
          if (deviceType.isEmpty()) {
            log.error("third party callback failed due to unknown device type index: {}",
                deviceTypeIndex);
            return;
          }
          allocateTask(deviceType.get(), backTaskCallbackRunnerI, serviceTemp, providerIndex);
        }
      }
    } catch (Exception e) {
      log.error("call back failed due to db error: {}", e.getMessage());
    }
  }

  @Transactional
  public void processFailCallBackTask(String serviceId, int op,
      ThirdPartyServiceRecordMgr.ServiceResult serviceResult, String error) {
    ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr = callBackComponents.getThirdPartyServiceRecordMgr();
    ThirdPartyServiceTemp serviceTemp = thirdPartyServiceRecordMgr.findRecordForUpdate(serviceId,
        op);
    thirdPartyServiceRecordMgr.parseServiceResult(serviceTemp, serviceResult, error);
  }

  private void allocateTask(ThirdPartyServiceRecordMgr.DeviceType deviceType,
      CallBackTaskCallbackRunnerI backTaskCallbackRunnerI,
      ThirdPartyServiceTemp serviceTemp, int provider) {
    int serviceType = serviceTemp.getServicetype();
    switch (deviceType) {
      case WATER -> {
        WaterMeterService waterMeterService = callBackComponents.getWaterMeterService();
        log.info("water call back task allocate!");
        Optional<WaterMeterInfo> meterInfoOptional = waterMeterService.findWMeterInfoByDeviceIdAndProvider(
            serviceTemp.getDeviceid(),
            provider, false);

        if (meterInfoOptional.isEmpty()) {
          log.error("water call back failed due to meter info miss, for record:{}", serviceTemp);
          return;
        }
        long meterInfoId = meterInfoOptional.get().getId();
        if (serviceType == ServiceTypeE.RECORD.getIndex()) {
          backTaskCallbackRunnerI.callbackWMeterRead(meterInfoId, waterMeterService);
        }
      }

      case ELECTRICITY -> {
        ElectronicMeterService electronicMeterService = callBackComponents.getElectronicMeterService();
        log.info("ele call back task allocate!");
        Optional<ElectronicMeterInfo> meterInfoOptional = electronicMeterService.findEMeterInfoByDeviceIdAndProvider(
            serviceTemp.getDeviceid(),
            provider, false);

        if (meterInfoOptional.isEmpty()) {
          log.error("ele call back failed due to meter info miss, for record:{}", serviceTemp);
          return;
        }
        long meterInfoId = meterInfoOptional.get().getId();
        if (serviceType == ServiceTypeE.OPEN.getIndex()
            || serviceType == ServiceTypeE.CLOSE.getIndex()) {
          backTaskCallbackRunnerI.callbackMeterSwitch(serviceTemp.getServicetype(), meterInfoId,
              electronicMeterService);
        }
      }

      case LOCKER -> {
        log.info("locker task");
        if (serviceType == ServiceTypeE.ADD.getIndex() || serviceType == ServiceTypeE.DEL.getIndex()
            || serviceType == ServiceTypeE.FROZEN.getIndex()
            || serviceType == ServiceTypeE.UNFROZEN.getIndex()) {
          backTaskCallbackRunnerI.callBackLockPwdOp(serviceTemp,
              callBackComponents.getThirdPartyServiceRecordMgr());
        }
      }
    }
  }
}
