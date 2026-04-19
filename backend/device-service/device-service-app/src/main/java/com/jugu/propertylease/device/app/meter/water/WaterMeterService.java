package com.jugu.propertylease.device.app.meter.water;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.enums.ServiceTypeE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.app.meter.MeterInstallTypeE;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.repository.WaterMeterRepository;
import com.jugu.propertylease.device.app.schedule.MeterScheduleRecordDetail;
import com.jugu.propertylease.device.app.task.DeviceTask;
import com.jugu.propertylease.device.common.entity.dto.MeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.dto.WMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.request.MeterBatchNotifyRequest;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
@Getter
public class WaterMeterService {

  private final WaterMeterProcessor waterMeterProcessor;

  private final WaterMeterRepository waterMeterRepository;

  public ReturnInfo adjustBoundRoom(RequestDataInfo<MeterInfoDTO> adjustRequest) {
    if (!adjustRequest.isValid()) {
      log.error(
          "fail to adjust w meter room due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    MeterInfoDTO dto = adjustRequest.getData();
    if (dto == null) {
      log.error("fail to adjust w meter room due to dto invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      waterMeterRepository.updateBoundRoom(dto.getId(), dto.getBoundRoomId(),
          adjustRequest.getUserName(),
          adjustRequest.getUserId(),
          Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to adjust w meter room due to ab error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 批量水表读数结算成功后回调
   *
   * @param wMeterSettlementBatchRequest userName, userId, and data which has been response by api:
   *                                     settleWaterMeterBatch
   * @return 整体是否成功，成功的话，Map<Boolean, Map<Long, ReturnInfo>里有局部成功和不成功的meter，不成功里ERROR可以读取失败原因
   */
  @Transactional
  public ReturnDataInfo<Map<Boolean, Map<Long, ReturnInfo>>> settleSuccessWaterNotifyBatch(
      MeterBatchNotifyRequest wMeterSettlementBatchRequest) {
    if (!wMeterSettlementBatchRequest.isValid()) {
      log.error(
          "fail to notify w settlement batch due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Set<MeterSettlementDTO> meterSettlementDTOS = wMeterSettlementBatchRequest.getData();
    if (Common.isCollectionInValid(meterSettlementDTOS)) {
      log.error(
          "fail to notify w settlement batch due to request input illegal!: settlement dto batch");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Set<Long> ids = meterSettlementDTOS.stream().map(MeterSettlementDTO::getId)
        .collect(Collectors.toSet());
    if (Common.isCollectionInValid(ids)) {
      log.error("fail to notify w settlement batch due to request input illegal!: ids in DTOs");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    try {
      Map<Long, WaterMeterInfo> meterInfoMap = waterMeterRepository.findWMeterInfoByIdsMapForUpdate(
          ids);
      if (meterInfoMap == null || meterInfoMap.isEmpty()) {
        log.error("fail to notify w settlement batch due to infos map invalid");
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }

      Map<Boolean, Map<Long, ReturnInfo>> results = new HashMap<>();
      if (ids.size() != meterInfoMap.size()) {
        Set<Long> missedIds = ids.stream().filter(id -> !meterInfoMap.containsKey(id))
            .collect(Collectors.toSet());
        if (!Common.isCollectionInValid(missedIds)) {
          Map<Long, ReturnInfo> failMeters = new HashMap<>();
          missedIds.forEach(id -> failMeters.put(id,
              ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "数据库中缺少此id数据")));
          results.put(false, failMeters);
        }
      }

      Set<WaterMeterInfo> shouldUpdateBatch = new HashSet<>();
      String userName = wMeterSettlementBatchRequest.getUserName();
      String userId = wMeterSettlementBatchRequest.getUserId();
      for (MeterSettlementDTO settlementDTO : meterSettlementDTOS) {
        long id = settlementDTO.getId();
        if (meterInfoMap.containsKey(id)) {
          WaterMeterInfo meterInfo = meterInfoMap.get(id);
          if (syncWMeterSettleInfo(settlementDTO, meterInfo, userId, userName)) {
            shouldUpdateBatch.add(meterInfo);
            Map<Long, ReturnInfo> successMeters = results.computeIfAbsent(true,
                v -> new HashMap<>());
            successMeters.put(id, ReturnInfo.success());
          } else {
            Map<Long, ReturnInfo> failMeters = results.computeIfAbsent(false, v -> new HashMap<>());
            failMeters.put(id, ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR,
                "时间信息或用量信息含非法值或结算冲突"));
          }
        }
      }
      waterMeterRepository.updateWMeterNotifyInfoBatch(shouldUpdateBatch);
      return ReturnDataInfo.successData(results);

    } catch (Exception e) {
      log.error("fail to notify w settlement batch due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 水表读数结算成功后回调
   *
   * @param wMeterSettlementRequest userName, userId, and data which has been response by api:
   *                                settleWaterMeter
   * @return 是否成功同步
   */
  @Transactional
  public ReturnInfo settleWSuccessNotify(
      RequestDataInfo<MeterSettlementDTO> wMeterSettlementRequest) {
    if (!wMeterSettlementRequest.isValid()) {
      log.error(
          "fail to notify w settlement due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      MeterSettlementDTO settlementDTO = wMeterSettlementRequest.getData();
      if (settlementDTO == null) {
        log.error("fail to notify w settlement due to dto null");
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      long id = settlementDTO.getId();
      WaterMeterInfo meterInfo = waterMeterRepository.findWMeterInfoForUpdate(id);
      if (meterInfo == null) {
        log.error("fail to find w meter info by id: {}", id);
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      if (syncWMeterSettleInfo(settlementDTO, meterInfo, wMeterSettlementRequest.getUserId(),
          wMeterSettlementRequest.getUserName())) {
        waterMeterRepository.update(meterInfo);
        return ReturnInfo.success();
      }
      return ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR,
          "时间信息或用量信息含非法值或结算冲突");
    } catch (Exception e) {
      log.error("fail to notify w settlement due to db error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 水表抄表结批量算请求, 按数据库当前数据返回
   *
   * @param ids 水表ids
   * @return Map<Boolean, Set < MeterSettlementDTO>> 主键为是否成功，值为成功的表与失败的表，errorInfo中记录失败原因
   */
  public ReturnDataInfo<Map<Boolean, Set<MeterSettlementDTO>>> settleWaterMeterBatch(
      Set<Long> ids) {
    if (Common.isCollectionInValid(ids) || ids.isEmpty()) {
      log.error("fail to settle w meter batch due to ids invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    try {
      Map<Long, WaterMeterInfo> waterMeterInfos = waterMeterRepository.findWMeterInfoByIdsMap(ids);
      if (waterMeterInfos == null || waterMeterInfos.isEmpty()) {
        log.error("fail to settle w meter batch due to infos map invalid");
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      Map<Boolean, Set<MeterSettlementDTO>> results = new HashMap<>();
      if (ids.size() != waterMeterInfos.size()) {
        Set<Long> missedIds = ids.stream().filter(id -> !waterMeterInfos.containsKey(id))
            .collect(Collectors.toSet());
        if (!Common.isCollectionInValid(missedIds)) {
          Set<MeterSettlementDTO> failMeters = new HashSet<>();
          missedIds.forEach(id -> {
            MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
            settlementDTO.setId(id).setReturnInfo(
                ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "数据库中缺少此id数据"));
            failMeters.add(settlementDTO);
          });
          results.put(false, failMeters);
        }
      }
      waterMeterInfos.forEach((id, info) -> {
        MeterSettlementDTO dto = genWMeterSettleDTO(info, id);
        if (dto.getReturnInfo().isSuccess()) {
          Set<MeterSettlementDTO> successMeters = results.computeIfAbsent(true,
              v -> new HashSet<>());
          successMeters.add(dto);
        } else {
          Set<MeterSettlementDTO> failMeters = results.computeIfAbsent(false, v -> new HashSet<>());
          failMeters.add(dto);
        }
      });
      return ReturnDataInfo.successData(results);
    } catch (Exception e) {
      log.error("fail to settle w meter batch due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 水表抄表结算请求, 按数据库当前数据返回（需要实时的话，先调用单独的抄表接口发起抄表后再调用服务确认接口完成后再调用此接口结算）
   *
   * @param id 水表id
   * @return MeterSettlementDTO 详见字段
   */
  public ReturnDataInfo<MeterSettlementDTO> settleWaterMeter(long id) {
    try {
      WaterMeterInfo waterMeterInfo = waterMeterRepository.findWMeterInfoById(id);
      if (waterMeterInfo == null) {
        log.error("fail to settle w meter due to info invalid for id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      MeterSettlementDTO dto = genWMeterSettleDTO(waterMeterInfo, id);
      return dto.getReturnInfo().isSuccess()
          ? ReturnDataInfo.successData(dto)
          : ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR,
              "时间信息或用量信息含非法值");

    } catch (Exception e) {
      log.error("fail to settle w meter due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  public ReturnDataInfo<ServiceRecordDTO> sendWMeterRecordRequest(
      RequestDataInfo<Long> wMeterRequest) {
    try {
      long id = wMeterRequest.getData();
      WaterMeterInfo waterMeterInfo = waterMeterRepository.findWMeterInfoById(id);
      if (waterMeterInfo == null) {
        log.error("fail to send w meter record request due to info invalid for id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      String deviceId = waterMeterInfo.getDeviceid();
      if (Common.isStringInValid(deviceId)) {
        log.error("fail to send w meter record request due to deviceId invalid for id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "deviceId无效，id：" + id);
      }
      int index = waterMeterInfo.getProviderop();
      Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(index);
      if (opE.isEmpty()) {
        log.error("fail to send w meter record request due to op invalid for index: {}", index);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "op无效，index：" + index);
      }
      return opE.get().getWaterRequestOp().readWaterRecordRequest(deviceId, waterMeterProcessor, id,
          wMeterRequest.getUserName(), wMeterRequest.getUserId());
    } catch (Exception e) {
      log.error("fail to send w meter record request due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  public Map<Integer, Map<Long, MeterScheduleRecordDetail>> processWMetersDailyRecordTask(
      String taskId) {
    List<WaterMeterInfo> waterMeterInfos = waterMeterRepository.findAvailableWMeterInfos();
    Map<Integer, Map<Long, MeterScheduleRecordDetail>> result = new HashMap<>();
    if (!Common.isCollectionInValid(waterMeterInfos)) {
      String curTime = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
      Map<Integer, List<Set<Pair<Long, String>>>> opBatchesMap = new HashMap<>();
      buildNeedRecordWMeterBatch(waterMeterInfos, opBatchesMap, curTime);
      if (!opBatchesMap.isEmpty()) {
        handleNeedRecordDailyWMeters(opBatchesMap, result, taskId);
      }
    }
    return result;
  }

  public ReturnInfo addNewWaterMeter(RequestDataInfo<WMeterInfoDTO> wMeterRequest) {
    WMeterInfoDTO wMeterInfoDTO = wMeterRequest.getData();
    if (!wMeterRequest.isValid() || wMeterInfoDTO == null) {
      log.error("fail to add new w meter due to request input illegal!");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    int provideOp = wMeterInfoDTO.getProviderOp();
    Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(provideOp);
    if (opE.isEmpty()) {
      log.error("fail to add new w meter due to illegal op");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    ProviderOpE provider = opE.get();
    String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    WaterRequestOp requestOp = provider.getWaterRequestOp();
    wMeterInfoDTO.setCreateUserId(wMeterRequest.getUserId());
    wMeterInfoDTO.setCreateUserName(wMeterRequest.getUserName());
    wMeterInfoDTO.setCreateTime(time);
    if (isNewWMeterDataValid(wMeterInfoDTO)) {
      WMeterCheckResponse checkResponse = requestOp.findWMeterInfoRequest(
          wMeterInfoDTO.getDeviceId());
      DeviceTask<WMeterCheckResponse, ReturnInfo> task = new DeviceTask<>(checkResponse) {
        @Override
        public boolean isTaskSuccess(WMeterCheckResponse responseData) {
          return responseData.isSuccess();
        }

        @Override
        public ReturnInfo successCallback(WMeterCheckResponse responseData) {
          double amount = responseData.getConsumeAmount();
          wMeterInfoDTO.setConsumeAmount(amount);
          wMeterInfoDTO.setPeriodConsumeAmount(amount);
          String recordTime = responseData.getRecordTime();
          wMeterInfoDTO.setPeriodConsumeStartTime(recordTime);
          wMeterInfoDTO.setProviderName(provider.getName());
          wMeterInfoDTO.setBoundTimeChange(time);
          wMeterInfoDTO.setConsumeRecordTime(recordTime);
          wMeterInfoDTO.setStatus(responseData.getOnOff());
          wMeterInfoDTO.setStatusTime(responseData.getOnOffTime());
          wMeterInfoDTO.setMeterType(responseData.getMeterType());
          wMeterInfoDTO.setGateWayStatus(responseData.getOnOffLine());
          try {
            waterMeterRepository.addNewWaterMeter(wMeterInfoDTO);
            return ReturnInfo.success();
          } catch (Exception e) {
            log.error("fail to add new w meter due to db error: {}", e.getMessage());
            return ReturnInfo.failByType(ErrorType.DB_ERROR);
          }
        }

        @Override
        public ReturnInfo failCallback(WMeterCheckResponse responseData) {
          log.error("fail to add new w meter due to third part check fail!");
          ErrorInfo errorInfo = responseData.getErrorInfo();
          return ReturnInfo.fail(errorInfo);
        }
      };
      return task.runTask();
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  public Optional<WaterMeterInfo> findWMeterInfoByDeviceIdAndProvider(String deviceId, int provider,
      boolean needLock) {
    if (Common.isStringInValid(deviceId) || provider == 0) {
      log.error("find w meter info failed due to invalid device id or provider!");
      return Optional.empty();
    }
    WaterMeterInfo waterMeterInfo =
        needLock ? waterMeterRepository.findWMeterInfoByDeviceIdAndProviderForUpdate(deviceId,
            provider)
            : waterMeterRepository.findWMeterInfoByDeviceIdAndProvider(deviceId, provider);
    if (waterMeterInfo == null) {
      log.error("find w meter info failed due to no match e meter info!");
      return Optional.empty();
    }
    return Optional.of(waterMeterInfo);
  }

  /**
   * 按业务内设备主键IDs查找水表信息
   *
   * @param ids 主键集合
   * @return 水表信息列表
   */
  public ReturnDataInfo<List<WMeterInfoDTO>> findWMeterInfoByIds(List<Long> ids) {
    if (Common.isCollectionInValid(ids)) {
      log.error("find w meter info failed due to ids invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<WaterMeterInfo> meterInfos = waterMeterRepository.findWMeterInfoByIds(ids);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find w meter info failed due to result invalid for input ids:{}", ids);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }

    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按业务内设备主键ID查找水表信息
   *
   * @param id 主键
   * @return 水表信息
   */
  public ReturnDataInfo<WMeterInfoDTO> findWMeterInfoById(long id) {
    WaterMeterInfo meterInfo = waterMeterRepository.findWMeterInfoById(id);
    if (meterInfo == null) {
      log.error("find w meter info failed due to no match result for input id:{}", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(toDTO(meterInfo));
  }

  /**
   * 按绑定房间ID查找房间相关水表列表
   *
   * @param roomIds 绑定的房源ID
   * @return 水表信息列表
   */
  public ReturnDataInfo<List<WMeterInfoDTO>> findWMeterInfoByRoomIds(List<String> roomIds) {
    if (Common.isCollectionInValid(roomIds)) {
      log.error("find w meter info failed due to roomIds invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<WaterMeterInfo> meterInfos = waterMeterRepository.findWMeterInfoByRoomIds(roomIds);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find w meter info failed due to result invalid for input roomIds:{}", roomIds);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按绑定房源ID查找房源对应水表信息
   *
   * @param roomId 房源ID
   * @return 对应的水表信息
   */
  public ReturnDataInfo<List<WMeterInfoDTO>> findWMeterInfoByRoomId(String roomId) {
    List<WaterMeterInfo> meterInfos = waterMeterRepository.findWMeterInfoByRoomId(roomId);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find w meter info failed due to result invalid for input roomId:{}", roomId);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 删除水表（无状态校验）
   *
   * @param delRequestForce 其中userName、userId必填，Long为水表主键id
   * @return 是否删除成功
   */
  public ReturnInfo delWMeter(RequestDataInfo<Long> delRequestForce) {
    if (!delRequestForce.isValid()) {
      log.error(
          "fail to adjust w meter due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    long id = delRequestForce.getData();
    try {
      WaterMeterInfo meterInfo = waterMeterRepository.findWMeterInfoById(id);
      if (meterInfo == null) {
        log.error("fail to del w meter info due to mismatch info by id: {}", id);
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      meterInfo.setIsdeleted(2);
      meterInfo.setDeleteuserid(delRequestForce.getUserId());
      meterInfo.setDeleteusername(delRequestForce.getUserName());
      meterInfo.setDeletetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      waterMeterRepository.update(meterInfo);
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to delete w meter due to db error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  public List<WaterMeterInfo> findWMeterInfoByDeviceIds(Collection<String> deviceIds) {
    List<WaterMeterInfo> infos = waterMeterRepository.findWMeterInfoByDeviceIds(deviceIds);
    return infos == null ? new ArrayList<>() : infos;
  }

  public int syncWMeterOnOffLine(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return waterMeterRepository.updateOnlineStatus(status, time, userName, userId, op, deviceId);
  }

  public void syncWMeterGatewayOnOffLine(int status, String time, String userName, String userId,
      int op, String deviceId) {
    waterMeterRepository.updateGateWayStatus(status, time, userName, userId, op, deviceId);
  }

  private boolean isNewWMeterDataValid(WMeterInfoDTO wMeterInfoDTO) {
    int installType = wMeterInfoDTO.getInstallType();
    if (!MeterInstallTypeE.isIndexValid(installType)) {
      log.error("fail to add new w meter due to illegal installType:{}", installType);
      return false;
    }
    String meterNo = wMeterInfoDTO.getMeterNo();
    String boundRoomId = wMeterInfoDTO.getBoundRoomId();
    String deviceId = wMeterInfoDTO.getDeviceId();
    String modelId = wMeterInfoDTO.getDeviceModelId();
    if (Common.isStringInValid(meterNo) || Common.isStringInValid(boundRoomId)
        || Common.isStringInValid(deviceId) || Common.isStringInValid(modelId)) {
      log.error(
          "fail to add new w meter due to illegal input-meterNo:{}, boundRoomId:{}, deviceId:{}",
          meterNo, boundRoomId, deviceId);
      return false;
    }
    return true;
  }

  private void buildNeedRecordWMeterBatch(List<WaterMeterInfo> waterMeterInfos,
      Map<Integer, List<Set<Pair<Long, String>>>> opBatchesMap, String curTime) {
    for (WaterMeterInfo waterMeterInfo : waterMeterInfos) {
      String recordTime = waterMeterInfo.getConsumerecordtime();
      String deviceId = waterMeterInfo.getDeviceid();
      long id = waterMeterInfo.getId();
      if (Common.isStringInValid(recordTime) || Common.isStringInValid(deviceId)) {
        log.error("w meter time or device id invalid for id: {}", id);
        continue;
      }
      Optional<String> validTimeTo = Common.findSysTimeAfter(recordTime, 180);
      if (validTimeTo.isEmpty()) {
        log.error("w meter time invalid for id: {}, time: {}", id, recordTime);
        continue;
      }
      if (validTimeTo.get().compareTo(curTime) < 0) {
        List<Set<Pair<Long, String>>> batches = opBatchesMap.computeIfAbsent(
            waterMeterInfo.getProviderop(), k -> new ArrayList<>());
        if (batches.isEmpty()) {
          Set<Pair<Long, String>> deviceIds = new HashSet<>();
          deviceIds.add(Pair.of(id, deviceId));
          batches.add(deviceIds);
        } else {
          Set<Pair<Long, String>> deviceIds = batches.get(batches.size() - 1);
          if (deviceIds.size() < 20) {
            deviceIds.add(Pair.of(id, deviceId));
          } else {
            Set<Pair<Long, String>> newDeviceIds = new HashSet<>();
            newDeviceIds.add(Pair.of(id, deviceId));
            batches.add(newDeviceIds);
          }
        }
      }

    }
  }

  private void handleNeedRecordDailyWMeters(
      Map<Integer, List<Set<Pair<Long, String>>>> opBatchesMap,
      Map<Integer, Map<Long, MeterScheduleRecordDetail>> result, String taskId) {
    Map<Integer, CompletableFuture<Map<Long, MeterScheduleRecordDetail>>> futureMap = new HashMap<>();
    for (Map.Entry<Integer, List<Set<Pair<Long, String>>>> opBatches : opBatchesMap.entrySet()) {
      int opIndex = opBatches.getKey();
      Optional<ProviderOpE> providerOpEOp = ProviderOpE.findProviderByIndex(opIndex);
      if (providerOpEOp.isEmpty()) {
        log.error("process W Meters Daily Record Task has invalid op:{}", opBatches);
        continue;
      }
      WaterRequestOp requestOp = providerOpEOp.get().getWaterRequestOp();
      List<Set<Pair<Long, String>>> batches = opBatches.getValue();
      CompletableFuture<Map<Long, MeterScheduleRecordDetail>> future = CompletableFuture.supplyAsync(
          () -> {
            Map<Long, MeterScheduleRecordDetail> futureResult = new HashMap<>();
            for (Set<Pair<Long, String>> batch : batches) {
              for (Pair<Long, String> meter : batch) {
                handleRecordWMeterDailyBatch(opIndex, meter, requestOp, futureResult, taskId);
              }
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                log.error(
                    "read w meter batch waiting is interrupted, result maybe not finished yet!");
                Thread.currentThread().interrupt();
              }
            }
            return futureResult;
          });
      futureMap.put(opIndex, future);
    }

    CompletableFuture<Void> allDone = CompletableFuture.allOf(
        futureMap.values().toArray(new CompletableFuture[0]));

    try {
      allDone.get(1200, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.error("w daily batch time out");
      futureMap.values().forEach(future -> {
        if (!future.isDone()) {
          future.cancel(true);
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    futureMap.forEach((key, future) -> {
      if (future.isDone() && !future.isCancelled()) {
        try {
          result.put(key, future.get());
        } catch (Exception e) {
          log.error("daily w meter record task:{} get result failed", key);
          result.put(key, Collections.emptyMap());
        }
      } else {
        log.error("daily w meter record task:{} cancelled not finish", key);
        result.put(key, Collections.emptyMap());
      }
    });
  }

  private void handleRecordWMeterDailyBatch(int opIndex, Pair<Long, String> meter,
      WaterRequestOp requestOp, Map<Long, MeterScheduleRecordDetail> futureResult, String taskId) {
    String deviceId = meter.getSecond();
    MeterScheduleRecordDetail detail = new MeterScheduleRecordDetail();
    long id = meter.getFirst();
    detail.setId(id);
    DeviceResponse response = requestOp.sendWaterRecordRequest(deviceId);
    if (response.isSuccess()) {
      String serviceId = response.getServiceId();
      detail.setResult(ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex());
      long tempId = waterMeterProcessor.getThirdPartyServiceRecordMgr()
          .recordNewServiceWithKey(serviceId, deviceId, ThirdPartyServiceRecordMgr.DeviceType.WATER,
              ServiceTypeE.RECORD.getIndex(), response.getServiceNote(), opIndex, taskId);
      detail.setTempId(tempId);
    } else {
      detail.setResult(ThirdPartyServiceRecordMgr.ServiceResult.FAIL.getIndex());
      detail.setErrorInfo(response.getErrorInfo().toString());
    }
    futureResult.put(id, detail);
  }

  private boolean checkSettleInfoValid(double consume, String recordTime, double periodConsume,
      String periodStTime) {
    return Double.compare(consume, 0d) > 0 && Double.compare(periodConsume, 0d) > 0
        && Double.compare(consume, periodConsume) >= 0 && !Common.isStringInValid(recordTime)
        && !Common.isStringInValid(periodStTime) && recordTime.compareTo(periodStTime) >= 0;
  }

  private MeterSettlementDTO genWMeterSettleDTO(WaterMeterInfo waterMeterInfo, long id) {
    double consume = waterMeterInfo.getConsumeamount();
    String recordTime = waterMeterInfo.getConsumerecordtime();
    double periodConsume = waterMeterInfo.getPeriodconsumeamount();
    String periodStTime = waterMeterInfo.getPeriodconsumestarttime();
    MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
    if (checkSettleInfoValid(consume, recordTime, periodConsume, periodStTime)) {
      settlementDTO.setId(id).setConsumeAmount(consume).setConsumeRecordTime(recordTime)
          .setPeriodConsumeAmount(periodConsume).setPeriodConsumeStartTime(periodStTime)
          .setReturnInfo(ReturnInfo.success());
      return settlementDTO;
    }
    log.error("fail to settle w meter due to info invalid time or consume for id: {}", id);
    settlementDTO.setReturnInfo(
        ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "时间信息或用量信息含非法值"));
    return settlementDTO;
  }

  private boolean syncWMeterSettleInfo(MeterSettlementDTO settlementDTO, WaterMeterInfo meterInfo,
      String userId, String userName) {
    double consume = settlementDTO.getConsumeAmount();
    String recordTime = settlementDTO.getConsumeRecordTime();
    double periodConsume = settlementDTO.getPeriodConsumeAmount();
    String periodStTime = settlementDTO.getPeriodConsumeStartTime();
    double periodConsumeN = meterInfo.getPeriodconsumeamount();
    String periodStTimeN = meterInfo.getPeriodconsumestarttime();
    if (checkSettleInfoValid(consume, recordTime, periodConsume, periodStTime)
        && !Common.isStringInValid(periodStTimeN)
        && Double.compare(periodConsume, periodConsumeN) == 0
        && periodStTime.compareTo(periodStTimeN) == 0) {
      meterInfo.setPeriodconsumeamount(consume);
      meterInfo.setPeriodconsumestarttime(recordTime);
      meterInfo.setUpdateuserid(userId);
      meterInfo.setUpdateusername(userName);
      meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      return true;
    }
    return false;
  }

  private WMeterInfoDTO toDTO(WaterMeterInfo waterMeterInfo) {
    WMeterInfoDTO infoDTO = new WMeterInfoDTO();
    infoDTO.setMeterType(waterMeterInfo.getMetertype());
    infoDTO.setGateWayStatus(waterMeterInfo.getGatewaystatus());
    infoDTO.setId(waterMeterInfo.getId())
        .setInstallType(waterMeterInfo.getInstalltype())
        .setMeterNo(waterMeterInfo.getMeterno())
        .setBoundRoomId(waterMeterInfo.getBoundroomid())
        .setBoundTimeChange(waterMeterInfo.getBoundtimechange())
        .setConsumeAmount(waterMeterInfo.getConsumeamount())
        .setConsumeRecordTime(waterMeterInfo.getConsumerecordtime())
        .setPeriodConsumeStartTime(waterMeterInfo.getPeriodconsumestarttime())
        .setPeriodConsumeAmount(waterMeterInfo.getPeriodconsumeamount())
        .setStatus(waterMeterInfo.getStatus())
        .setStatusTime(waterMeterInfo.getStatustime())
        .setDeviceId(waterMeterInfo.getDeviceid())
        .setDeviceModelId(waterMeterInfo.getDevicemodelid())
        .setProviderName(waterMeterInfo.getProvidername())
        .setProviderOp(waterMeterInfo.getProviderop())
        .setCollectorId(waterMeterInfo.getCollectorid())
        .setCreateUserId(waterMeterInfo.getCreateuserid())
        .setCreateUserName(waterMeterInfo.getCreateusername())
        .setCreateTime(waterMeterInfo.getCreatetime())
        .setUpdateUserId(waterMeterInfo.getUpdateuserid())
        .setUpdateUserName(waterMeterInfo.getUpdateusername())
        .setUpdateTime(waterMeterInfo.getUpdatetime())
        .setIsDeleted(waterMeterInfo.getIsdeleted())
        .setDeleteUserId(waterMeterInfo.getDeleteuserid())
        .setDeleteUserName(waterMeterInfo.getDeleteusername())
        .setDeleteTime(waterMeterInfo.getDeletetime());
    return infoDTO;
  }
}
