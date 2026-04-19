package com.jugu.propertylease.device.app.schedule.water;

import com.google.gson.JsonSyntaxException;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.enums.ServiceTypeE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.DeviceScheduleTaskInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.ScheduleTaskRepository;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.schedule.MeterScheduleRecordDetail;
import com.jugu.propertylease.device.app.schedule.ScheduleResultE;
import com.jugu.propertylease.device.app.schedule.ScheduleStatusE;
import com.jugu.propertylease.device.app.schedule.ScheduleTaskNameE;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Log4j2
@RequiredArgsConstructor
public class WMeterScheduleTaskMgr {

  private static final int ONE_DAY_MINS = 1440;
  private static final int START_CHECK_MINS = 30;
  private final WaterMeterService waterMeterService;
  private final ScheduleTaskRepository scheduleTaskRepository;
  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  @Async
  @Scheduled(cron = "0 0 0 * * ?")
  public void recordWMetersDailyTask() {
    log.info("daily record w meter task start!");
    String taskId =
        System.currentTimeMillis() + "-" + ThirdPartyServiceRecordMgr.DeviceType.WATER.getIndex()
            + "-" + ServiceTypeE.RECORD.getIndex();
    try {
      scheduleTaskRepository.addNewTask(taskId, ScheduleTaskNameE.WaterMeterDailyRecord.name());
      Map<Integer, Map<Long, MeterScheduleRecordDetail>> records = waterMeterService.processWMetersDailyRecordTask(
          taskId);
      Map<Long, Long> pendingTempIds = new HashMap<>();
      Map<Long, String> failInfo = new HashMap<>();
      records.forEach((op, batch) -> {
        if (batch.isEmpty()) {
          List<ThirdPartyServiceTemp> missedTemp = thirdPartyServiceRecordMgr.findIdsByAddInfoAndOp(
              taskId, op);
          if (!Common.isCollectionInValid(missedTemp)) {
            Set<String> deviceIds = missedTemp.stream().map(ThirdPartyServiceTemp::getDeviceid)
                .collect(Collectors.toSet());
            List<WaterMeterInfo> infos = waterMeterService.findWMeterInfoByDeviceIds(deviceIds);
            Map<String, WaterMeterInfo> infoMap = infos.stream()
                .filter(info -> (int) info.getProviderop() == op)
                .collect(
                    Collectors.toMap(WaterMeterInfo::getDeviceid, info -> info, (ex, re) -> ex));
            missedTemp.forEach(temp -> {
              long tempId = temp.getId();
              WaterMeterInfo info = infoMap.getOrDefault(temp.getDeviceid(), null);
              if (info == null) {
                failInfo.put(-1L, "meter info miss for service:" + temp);
              } else {
                pendingTempIds.put(tempId, info.getId());
              }
            });
          }
        } else {
          batch.forEach((id, info) -> {
            int result = info.getResult();
            if (result == ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex()) {
              pendingTempIds.put(info.getTempId(), id);
            } else {
              failInfo.put(id, info.getErrorInfo());
            }
          });
        }
      });
      WMeterDailyRecordTaskDetail taskDetail = new WMeterDailyRecordTaskDetail();
      Set<Long> successIds = new HashSet<>();
      taskDetail.setPendingTempIds(pendingTempIds);
      taskDetail.setFailInfo(failInfo);
      taskDetail.setSuccessIds(successIds);
      scheduleTaskRepository.processTask(taskId, GsonFactory.toJson(taskDetail));
      log.info("daily record w meter task submitted!");
    } catch (Exception e) {
      log.error("daily record w meter failed due to db error:{}", e.getMessage());
    }
  }

  @Async
  @Scheduled(cron = "0 0 */4 * * ?")
  public void wMeterDailyRecordResultsSync() {
    log.info("results async task for daily record w meter start!");
    try {
      List<DeviceScheduleTaskInfo> pendingTasks = scheduleTaskRepository.findPendingTasksByType(
          ScheduleTaskNameE.WaterMeterDailyRecord.name());
      if (Common.isCollectionInValid(pendingTasks)) {
        log.info("results async task for daily record w meter finish, nothing async!");
        return;
      }
      for (DeviceScheduleTaskInfo taskInfo : pendingTasks) {
        String curTime = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
        String stTime = taskInfo.getTasksttime();
        String taskId = taskInfo.getId();
        if (Common.isStringInValid(stTime)) {
          processStTimeInvalidTask(stTime, taskInfo, curTime, taskId);
          continue;
        }
        Optional<String> timeOneDay = Common.findSysTimeAfter(stTime, ONE_DAY_MINS);
        Optional<String> stCheckTime = Common.findSysTimeAfter(stTime, START_CHECK_MINS);
        if (timeOneDay.isEmpty() || stCheckTime.isEmpty()) {
          processStTimeInvalidTask(stTime, taskInfo, curTime, taskId);
          continue;
        }
        if (isTaskTimeOut(timeOneDay.get(), curTime)) {
          processTimeoutTask(taskInfo, curTime, taskId);
          continue;
        }
        if (hasTaskAlreadyReachAsyncTime(stCheckTime.get(), curTime)) {
          String detail = taskInfo.getTaskdetail();
          Optional<WMeterDailyRecordTaskDetail> taskDetailOp = parseToTaskDetail(detail);
          if (taskDetailOp.isEmpty()) {
            processDetailInvalidTask(taskInfo, taskId, detail, curTime);
            continue;
          }
          WMeterDailyRecordTaskDetail taskDetail = taskDetailOp.get();
          Map<Long, Long> pendingTemps = taskDetail.getPendingTempIds();
          Map<Long, String> failInfo = taskDetail.getFailInfo();

          if (pendingTemps == null || pendingTemps.isEmpty()) {
            processFinishedTask(failInfo, taskInfo, curTime, taskId);
            continue;
          }
          processUpdatedDetailTask(pendingTemps, taskDetail, failInfo, taskInfo, curTime, taskId);
        }
      }
      log.info("results async task for daily record w meter finish!");
    } catch (Exception e) {
      log.error("results async task for daily record w meter failed due to db error:{}",
          e.getMessage());
    }
  }

  private void setFailedTask(String curTime, DeviceScheduleTaskInfo taskInfo) {
    taskInfo.setTaskresult(ScheduleResultE.REVIEW.name());
    taskInfo.setTaskstatus(ScheduleStatusE.FINISHED.name());
    taskInfo.setTaskedtime(curTime);
  }

  private void separateTaskDetailByTempResults(long tempId, long meterId,
      ThirdPartyServiceTemp serviceTemp,
      Map<Long, Long> pendingTempsNew, Set<Long> successIdsNew,
      Map<Long, String> failInfoNew, Set<Long> needDeleteTemps) {
    int result = serviceTemp.getServiceresult();
    if (result == ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex()) {
      pendingTempsNew.put(tempId, meterId);
    } else if (result == ThirdPartyServiceRecordMgr.ServiceResult.SUCCESS.getIndex()) {
      needDeleteTemps.add(tempId);
      successIdsNew.add(meterId);
    } else if (result == ThirdPartyServiceRecordMgr.ServiceResult.FAIL.getIndex()) {
      needDeleteTemps.add(tempId);
      failInfoNew.put(meterId, serviceTemp.getServiceerror());
    } else {
      needDeleteTemps.add(tempId);
      failInfoNew.put(meterId, "unknown result:" + result);
    }
  }

  private String generateUpdatedDetailInfo(WMeterDailyRecordTaskDetail taskDetail,
      Map<Long, Long> pendingTempsNew, Map<Long, String> failInfo) {
    taskDetail.setPendingTempIds(pendingTempsNew);
    taskDetail.setFailInfo(failInfo);
    taskDetail.setFailInfo(failInfo);
    return GsonFactory.toJson(taskDetail);
  }

  private void setFinishedTaskResultByFailInfo(Map<Long, String> failInfo,
      DeviceScheduleTaskInfo taskInfo) {
    if (failInfo != null && !failInfo.isEmpty()) {
      taskInfo.setTaskresult(ScheduleResultE.REVIEW.name());
    } else {
      taskInfo.setTaskresult(ScheduleResultE.SUCCESS.name());
    }
  }

  private void processTimeoutTask(DeviceScheduleTaskInfo taskInfo, String curTime, String taskId) {
    if (taskInfo.getTaskstatus().equals(ScheduleStatusE.CREATED.name())) {
      log.error("daily record w meter task:{} failed due to abnormal created", taskId);
      taskInfo.setTaskdetail("abnormal created task");
    }
    setFailedTask(curTime, taskInfo);
    updateAndSyncEntireTask(taskId, taskInfo);
  }

  private void processStTimeInvalidTask(String stTime, DeviceScheduleTaskInfo taskInfo,
      String curTime, String taskId) {
    log.error("daily record w meter task:{} failed due to abnormal stTime:{}", taskId, stTime);
    taskInfo.setTaskdetail(
        String.format("abnormal stTime:%s task, taskDetail:%s", stTime, taskInfo.getTaskdetail()));
    setFailedTask(curTime, taskInfo);
    updateAndSyncEntireTask(taskId, taskInfo);
  }

  private void processDetailInvalidTask(DeviceScheduleTaskInfo taskInfo, String taskId,
      String detail, String curTime) {
    log.error("daily record w meter task:{} failed due to abnormal detail:{}", taskId, detail);
    taskInfo.setTaskdetail("abnormal detail task");
    setFailedTask(curTime, taskInfo);
    updateAndSyncEntireTask(taskId, taskInfo);
  }

  private void processFinishedTask(Map<Long, String> failInfo, DeviceScheduleTaskInfo taskInfo,
      String curTime, String taskId) {
    setFinishedTaskResultByFailInfo(failInfo, taskInfo);
    taskInfo.setTaskstatus(ScheduleStatusE.FINISHED.name());
    taskInfo.setTaskedtime(curTime);
    updateAndSyncEntireTask(taskId, taskInfo);
  }

  private Optional<WMeterDailyRecordTaskDetail> parseToTaskDetail(String detail) {
    if (Common.isStringInValid(detail)) {
      return Optional.empty();
    }
    try {
      WMeterDailyRecordTaskDetail taskDetail = GsonFactory.fromJson(detail,
          WMeterDailyRecordTaskDetail.class);
      return Optional.of(taskDetail);
    } catch (JsonSyntaxException e) {
      return Optional.empty();
    }
  }

  private boolean isTaskTimeOut(String timeOneDay, String curTime) {
    return timeOneDay.compareTo(curTime) < 0;
  }

  private boolean hasTaskAlreadyReachAsyncTime(String stCheckTime, String curTime) {
    return stCheckTime.compareTo(curTime) < 0;
  }

  private void fillNewDetailInfos(Map<Long, Long> pendingTemps,
      Map<Long, ThirdPartyServiceTemp> temps,
      Map<Long, String> failInfoNew, Map<Long, Long> pendingTempsNew,
      Set<Long> successIdsNew, Set<Long> needDeleteTemps) {
    pendingTemps.forEach((tempId, meterId) -> {
      ThirdPartyServiceTemp serviceTemp = temps.getOrDefault(tempId, null);
      if (serviceTemp == null) {
        failInfoNew.put(meterId, "service temp miss");
      } else {
        separateTaskDetailByTempResults(tempId, meterId, serviceTemp, pendingTempsNew,
            successIdsNew, failInfoNew, needDeleteTemps);
      }
    });
  }

  private void processUpdatedDetailTask(Map<Long, Long> pendingTemps,
      WMeterDailyRecordTaskDetail taskDetail, Map<Long, String> failInfo,
      DeviceScheduleTaskInfo taskInfo, String curTime, String taskId) {
    Map<Long, ThirdPartyServiceTemp> temps = thirdPartyServiceRecordMgr.findServiceTempsByIds(
        pendingTemps.keySet());
    Map<Long, Long> pendingTempsNew = new HashMap<>();
    Map<Long, String> failInfoNew = new HashMap<>();
    Set<Long> successIdsNew = new HashSet<>();
    Set<Long> needDeleteTemps = new HashSet<>();
    fillNewDetailInfos(pendingTemps, temps, failInfoNew, pendingTempsNew, successIdsNew,
        needDeleteTemps);
    Set<Long> successIds = taskDetail.getSuccessIds();
    if (successIds == null) {
      successIds = new HashSet<>();
    }
    if (failInfo == null) {
      failInfo = new HashMap<>();
    }
    successIds.addAll(successIdsNew);
    failInfo.putAll(failInfoNew);
    taskInfo.setTaskdetail(generateUpdatedDetailInfo(taskDetail, pendingTempsNew, failInfo));
    if (pendingTempsNew.isEmpty()) {
      processFinishedTask(failInfo, taskInfo, curTime, taskId);
      return;
    }
    updateAndSyncTaskDetail(needDeleteTemps, taskInfo);
  }

  @Transactional
  void updateAndSyncEntireTask(String taskId, DeviceScheduleTaskInfo taskInfo) {
    thirdPartyServiceRecordMgr.deleteServiceByAddInfo(taskId);
    scheduleTaskRepository.update(taskInfo);
  }

  @Transactional
  void updateAndSyncTaskDetail(Set<Long> needDeleteTemps, DeviceScheduleTaskInfo taskInfo) {
    thirdPartyServiceRecordMgr.deleteServiceByAddIds(needDeleteTemps);
    scheduleTaskRepository.update(taskInfo);
  }
}
