package com.jugu.propertylease.device.app.meter.electricity;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.meter.MeterSwitchStatus;
import com.jugu.propertylease.device.app.meter.MeterUtil;
import com.jugu.propertylease.device.app.repository.ElectronicMeterRepository;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Log4j2
public class ElectronicSwitchProcessor {

  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  private final ElectronicMeterRepository electronicMeterRepository;

  private final ConcurrentHashMap<Long, ReentrantLock> switchLockMap = new ConcurrentHashMap<>();

  @Transactional
  public Optional<Integer> parseAsyncEMeterSwitchTaskResult(String serviceId, int providerOp,
      long id, int statusIndex, int targetState,
      String stateTime, String userName, String userId) {
    ThirdPartyServiceTemp serviceTemp = thirdPartyServiceRecordMgr.findRecordForUpdate(serviceId,
        providerOp);
    ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoForUpdate(id);
    if (serviceTemp != null && meterInfo != null) {
      int curSwitchStatus = meterInfo.getSwitchstatus();
      if (curSwitchStatus == statusIndex) {
        thirdPartyServiceRecordMgr.parseServiceResult(serviceTemp,
            ThirdPartyServiceRecordMgr.ServiceResult.SUCCESS, "");
        setAndUpdateSwitchStatus(meterInfo, targetState, stateTime, userName, userId);
        return Optional.of(targetState);
      } else {
        return Optional.of(curSwitchStatus);
      }
    }
    return Optional.empty();
  }

  public ReturnDataInfo<Integer> runAsyncEMeterSwitchTask(SwitchTaskRunner taskRunner,
      EMeterSwitchResponse.SwitchTypeE switchTypeE,
      String userName, String userId, ElectronicMeterInfo meterInfo) {
    long id = meterInfo.getId();
    ReentrantLock lock = switchLockMap.computeIfAbsent(id, k -> new ReentrantLock());
    if (!lock.tryLock()) {
      return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "电表闸门占用中，请稍后重试");
    }
    try {
      int curState = meterInfo.getSwitchstatus();
      if (curState == MeterSwitchStatus.CLOSING.getIndex()
          || curState == MeterSwitchStatus.OPENING.getIndex()) {
        return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR,
            "电表正在" + curState + "当前操作不可执行");
      }
      int switchType = switchTypeE.getSwitchType();
      if (curState == switchType) {
        log.warn("e meter:{} is already in target status, no changes needed!", id);
        return ReturnDataInfo.successData(curState);
      }
      MeterSwitchStatus middleStatus = MeterUtil.findMiddleStatusBySwitchType(switchTypeE);
      int middleStatusIndex = middleStatus.getIndex();
      electronicMeterRepository.updateMeterMiddleState(middleStatusIndex, userId, userName,
          meterInfo);
      String deviceId = meterInfo.getDeviceid();
      int providerOp = meterInfo.getProviderop();
      EMeterSwitchResponse switchResponse = taskRunner.submitAsyncTask(deviceId);
      if (switchResponse.isSuccess()) {
        String serviceId = switchResponse.getServiceId();
        thirdPartyServiceRecordMgr.recordNewService(serviceId, deviceId,
            ThirdPartyServiceRecordMgr.DeviceType.ELECTRICITY,
            switchType, switchResponse.getServiceNote(), providerOp, "");
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          log.error(
              "adjust e meter waiting is interrupted, result maybe not finished yet for id:{}!",
              id);
          Thread.currentThread().interrupt();
        }
        EMeterCheckResponse checkResponse = taskRunner.runSyncStateTask(deviceId);
        if (!checkResponse.isSuccess()) {
          log.error(
              "adjust e meter delay check fail due to third party, should manual refresh for id:{}!",
              id);
          return ReturnDataInfo.failData(checkResponse.getErrorInfo());
        }
        int enableState = checkResponse.getEnableState();
        if (enableState == switchType) {
          Optional<Integer> result = parseAsyncEMeterSwitchTaskResult(serviceId, providerOp, id,
              middleStatusIndex, enableState,
              checkResponse.getEnableStateTime(), userName, userId);
          if (result.isPresent()) {
            int finalState = result.get();
            if (finalState == enableState) {
              return ReturnDataInfo.successData(finalState);
            }
            return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR,
                "target:" + enableState + "now:" + finalState);
          }
          log.error("e meter info missed for id: {}", id);
          return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR,
              "异步服务记录或电表数据信息缺失");
        }
        return ReturnDataInfo.successData(middleStatusIndex);
      }
      return ReturnDataInfo.failData(switchResponse.getErrorInfo());
    } finally {
      lock.unlock();
      cleanUpUnusedLock(id, lock);
    }
  }

  public ReturnDataInfo<Integer> runSyncEMeterSwitchTask(SwitchTaskRunner taskRunner,
      String userName, String userId,
      ElectronicMeterInfo meterInfo, EMeterSwitchResponse.SwitchTypeE switchTypeE) {
    long id = meterInfo.getId();
    ReentrantLock lock = switchLockMap.computeIfAbsent(id, k -> new ReentrantLock());
    if (!lock.tryLock()) {
      return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "电表闸门占用中，请稍后重试");
    }
    try {
      int curState = meterInfo.getSwitchstatus();
      if (curState == switchTypeE.getSwitchType()) {
        log.warn("e meter:{} is already in target status, no changes needed!", id);
        return ReturnDataInfo.successData(curState);
      }
      String deviceId = meterInfo.getDeviceid();
      EMeterCheckResponse response = taskRunner.runSyncStateTask(deviceId);
      if (response.isSuccess()) {
        Optional<Integer> result = syncEMeterSwitchTaskResult(id, response.getEnableState(),
            response.getEnableStateTime(), userName, userId);
        if (result.isPresent()) {
          return ReturnDataInfo.successData(result.get());
        }
        log.error("e meter info missed for id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "电表数据信息缺失");
      }
      return ReturnDataInfo.failData(response.getErrorInfo());
    } finally {
      lock.unlock();
      cleanUpUnusedLock(id, lock);
    }
  }

  @Transactional
  public Optional<Integer> syncEMeterSwitchTaskResult(long id, int targetState, String stateTime,
      String userName, String userId) {
    ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoForUpdate(id);
    if (meterInfo != null) {
      int curSwitchStatus = meterInfo.getSwitchstatus();
      if (curSwitchStatus != targetState) {
        setAndUpdateSwitchStatus(meterInfo, targetState, stateTime, userName, userId);
      }
      return Optional.of(targetState);
    }
    return Optional.empty();
  }

  private void setAndUpdateSwitchStatus(ElectronicMeterInfo meterInfo, int targetState,
      String stateTime, String userName, String userId) {
    meterInfo.setSwitchstatus(targetState);
    meterInfo.setEnablestatetime(stateTime);
    meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    meterInfo.setUpdateuserid(userId);
    meterInfo.setUpdateusername(userName);
    electronicMeterRepository.update(meterInfo);
  }

  private void cleanUpUnusedLock(long id, ReentrantLock lock) {
    if (!lock.isLocked() && lock.getQueueLength() == 0) {
      switchLockMap.remove(id, lock);
    }
  }

  public interface SwitchTaskRunner {

    EMeterSwitchResponse submitAsyncTask(String deviceId);

    EMeterCheckResponse runSyncStateTask(String deviceId);
  }
}
