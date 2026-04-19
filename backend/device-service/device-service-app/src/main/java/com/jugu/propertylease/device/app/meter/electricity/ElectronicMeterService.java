package com.jugu.propertylease.device.app.meter.electricity;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.repository.ElectronicMeterRepository;
import com.jugu.propertylease.device.app.task.DeviceTask;
import com.jugu.propertylease.device.common.entity.dto.EMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import com.jugu.propertylease.device.common.entity.request.MeterBatchNotifyRequest;
import com.jugu.propertylease.device.common.entity.request.MeterRequest;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
public class ElectronicMeterService {

  private static final int BATCH_SIZE = 8;

  private final ElectronicSwitchProcessor switchProcessor;

  private final ElectronicMeterRepository electronicMeterRepository;

  public ReturnInfo adjustBoundRoom(RequestDataInfo<MeterInfoDTO> adjustRequest) {
    if (!adjustRequest.isValid()) {
      log.error(
          "fail to adjust e meter room due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    MeterInfoDTO dto = adjustRequest.getData();
    if (dto == null) {
      log.error("fail to adjust e meter room due to dto invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      electronicMeterRepository.updateBoundRoom(dto.getId(), dto.getBoundRoomId(),
          adjustRequest.getUserName(),
          adjustRequest.getUserId(),
          Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to adjust e meter room due to ab error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 电表读数结算成功后回调
   *
   * @param eMeterSettlementRequest userName, userId, and data which has been response by api:
   *                                settleElectronicMeter
   * @return 是否成功同步
   */
  @Transactional
  public ReturnInfo settleSuccessNotify(
      RequestDataInfo<MeterSettlementDTO> eMeterSettlementRequest) {
    if (!eMeterSettlementRequest.isValid()) {
      log.error(
          "fail to notify e settlement due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      MeterSettlementDTO settlementDTO = eMeterSettlementRequest.getData();
      if (settlementDTO == null) {
        log.error("fail to notify e settlement due to dto null");
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      long id = settlementDTO.getId();
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoForUpdate(id);
      if (meterInfo == null) {
        log.error("fail to find e meter info by id: {}", id);
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      ReturnInfo asyncResult = asyncEMeterSettleSuccessNotifyInfoToMeterInfo(settlementDTO,
          meterInfo, eMeterSettlementRequest.getUserName(),
          eMeterSettlementRequest.getUserId());
      if (asyncResult.isSuccess()) {
        electronicMeterRepository.update(meterInfo);
      }
      return asyncResult;
    } catch (Exception e) {
      log.error("fail to notify e settlement due to db error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 批量电表读数结算成功后回调
   *
   * @param eMeterSettlementBatchRequest userName, userId, and data which has been response by api:
   *                                     settleElectronicMeterBatch
   * @return 整体是否成功，成功的话，Map<Boolean, Map<Long, ReturnInfo>里有局部成功和不成功的meter，不成功里ERROR可以读取失败原因
   */
  @Transactional
  public ReturnDataInfo<Map<Boolean, Map<Long, ReturnInfo>>> settleSuccessNotifyBatch(
      MeterBatchNotifyRequest eMeterSettlementBatchRequest) {
    if (!eMeterSettlementBatchRequest.isValid()) {
      log.error(
          "fail to notify e settlement batch due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Set<MeterSettlementDTO> notifyBatch = eMeterSettlementBatchRequest.getData();
    if (Common.isCollectionInValid(notifyBatch)) {
      log.error("fail to notify e settlement batch due to request batch null or empty");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Map<Boolean, Map<Long, ReturnInfo>> results = new HashMap<>();
    Map<Long, MeterSettlementDTO> shouldUpdateIds = new HashMap<>();
    for (MeterSettlementDTO settlementDTO : notifyBatch) {
      if (settlementDTO == null) {
        log.error("fail to notify e settlement batch due to batch contains null dto");
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      shouldUpdateIds.put(settlementDTO.getId(), settlementDTO);
    }
    try {
      Map<Long, ElectronicMeterInfo> infoMap = electronicMeterRepository.findEMeterInfoByIdsMapForUpdate(
          shouldUpdateIds.keySet());
      if (infoMap == null || infoMap.isEmpty()) {
        log.error("fail to notify e meter settlement batch due to db info missing for ids:{}",
            shouldUpdateIds);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "数据库信息缺失");
      }
      if (shouldUpdateIds.size() != infoMap.size()) {
        shouldUpdateIds.forEach((k, v) -> {
          if (!infoMap.containsKey(k)) {
            log.error("fail to notify e settlement due to db data missing for id: {}", k);
            Map<Long, ReturnInfo> failResults = results.computeIfAbsent(false,
                key -> new HashMap<>());
            failResults.put(k,
                ReturnInfo.failByType(ErrorType.SERIOUS_ERROR, "数据库信息缺失id:" + k));
          }
        });
      }
      Set<ElectronicMeterInfo> shouldUpdateDbBatch = new HashSet<>();
      infoMap.forEach((id, info) -> {
        ReturnInfo asyncRes = asyncEMeterSettleSuccessNotifyInfoToMeterInfo(shouldUpdateIds.get(id),
            info,
            eMeterSettlementBatchRequest.getUserName(), eMeterSettlementBatchRequest.getUserId());
        if (asyncRes.isSuccess()) {
          shouldUpdateDbBatch.add(info);
          Map<Long, ReturnInfo> successResult = results.computeIfAbsent(true,
              key -> new HashMap<>());
          successResult.put(id, asyncRes);
        } else {
          Map<Long, ReturnInfo> failResults = results.computeIfAbsent(false,
              key -> new HashMap<>());
          failResults.put(id, asyncRes);
        }
      });
      electronicMeterRepository.updateEMeterNotifyInfoBatch(shouldUpdateDbBatch);
      return ReturnDataInfo.successData(results);

    } catch (Exception e) {
      log.error("fail to notify e meter settlement batch due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 批量抄表结算接口，
   *
   * @param settleBatchRequest userId, userName，以及set<long>中为需要结算抄表的ids
   * @return 整体是否成功，或局部成功，局部成功看Map<Boolean, Set<MeterSettlementDTO>主键区分成功与失败集合，MeterSettlementDTO
   * 中的ReturnInfo记录单条失败的原因。成功：1. 在一小时以内的记录数据不会调用第三方查询，2.超过一小时，会调用查询更新后返回成功。失败：详见
   * returnInfo中的Error结构部分内提示。
   */
  @Transactional
  public ReturnDataInfo<Map<Boolean, Set<MeterSettlementDTO>>> settleElectronicMeterBatch(
      RequestDataInfo<Set<Long>> settleBatchRequest) {
    if (!settleBatchRequest.isValid()) {
      log.error(
          "fail to settle ele meter batch due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Set<Long> ids = settleBatchRequest.getData();
    if (Common.isCollectionInValid(ids)) {
      log.error(
          "fail to settle ele meter batch due to request input illegal!:batch set null or empty");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    Map<Boolean, Set<MeterSettlementDTO>> results = new HashMap<>();
    try {
      Map<Long, ElectronicMeterInfo> fullDataInfoBatch = electronicMeterRepository.findEMeterInfoByIdsMap(
          ids);
      if (fullDataInfoBatch == null || fullDataInfoBatch.isEmpty()) {
        log.error("fail to settle ele meter batch due to db result null or empty");
        return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR,
            "请求中的ids在数据库中不存在数据");
      }
      filterDBMismatchResults(fullDataInfoBatch, results, ids);
      Map<ProviderOpE, List<Set<Pair<Long, String>>>> needUpdateDevicesBatch = new HashMap<>();
      separateAndAllocateEMeterSettleFromDBBatch(fullDataInfoBatch, results,
          needUpdateDevicesBatch);
      if (needUpdateDevicesBatch.isEmpty()) {
        return ReturnDataInfo.successData(results);
      }
      try {
        Map<Long, EMeterCheckResponse> readResults = runNeedUpdateDeviceBatchTask(
            needUpdateDevicesBatch);
        Set<Long> idsShouldUpdate = new HashSet<>();
        separateAndAllocateEMeterSettleFromBatchTaskResult(readResults, idsShouldUpdate, results);
        if (idsShouldUpdate.isEmpty()) {
          return ReturnDataInfo.successData(results);
        }
        Map<Long, ElectronicMeterInfo> dbBeforeUpdateResults = electronicMeterRepository.findEMeterInfoByIdsMapForUpdate(
            idsShouldUpdate);
        filterDBMismatchResults(dbBeforeUpdateResults, results, idsShouldUpdate);
        if (dbBeforeUpdateResults.isEmpty()) {
          return ReturnDataInfo.successData(results);
        }
        Set<MeterSettlementDTO> batchUpdateSet = new HashSet<>();
        separateShouldUpdateEMeterAndHasBeenUpdated(dbBeforeUpdateResults, readResults, results,
            batchUpdateSet);
        electronicMeterRepository.updateEMeterReadInfoBatch(batchUpdateSet,
            settleBatchRequest.getUserName(), settleBatchRequest.getUserId());
        return ReturnDataInfo.successData(results);
      } catch (ExecutionException | InterruptedException | TimeoutException e) {
        log.error("fail to settle e meter batch due to batch read timeout error: {}",
            e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "异步任务未在规定时间内响应");
      }
    } catch (Exception e) {
      log.error("fail to settle e meter batch due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 电表抄表结算请求,超出60分钟抄表数据则请求第三方更新数据，反之直接拿库
   *
   * @param eleMeterRequest userId, userName, 电表id
   * @return MeterSettlementDTO 详见字段
   */
  @Transactional
  public ReturnDataInfo<MeterSettlementDTO> settleElectronicMeter(
      RequestDataInfo<Long> eleMeterRequest) {
    if (!eleMeterRequest.isValid()) {
      log.error(
          "fail to settle ele meter due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    try {
      long id = eleMeterRequest.getData();
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoForUpdate(id);
      if (meterInfo == null) {
        log.error("fail to find e meter info by id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      String recordTime = meterInfo.getConsumerecordtime();
      if (Common.isStringInValid(recordTime)) {
        log.error("fail to settle e meter due to serious problem: recordTime is invalid");
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "电表抄表时间缺失");
      }
      Optional<Boolean> needCheckAndUpdatedOp = needCheckForUpdate(recordTime);
      if (needCheckAndUpdatedOp.isEmpty()) {
        log.error("fail to settle e meter due to serious problem: recordTime format is invalid");
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "电表抄表时间格式错误");
      }
      MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
      settlementDTO.setId(id).setPeriodConsumeAmount(meterInfo.getPeriodconsumeamount())
          .setPeriodConsumeStartTime(meterInfo.getPeriodconsumestarttime());
      // 超过一小时未更新，第三方读取同步后返还，一小时以内则返回当前记录值
      if (needCheckAndUpdatedOp.get()) {
        String deviceId = meterInfo.getDeviceid();
        Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(meterInfo.getProviderop());
        if (Common.isStringInValid(deviceId) || opE.isEmpty()) {
          log.error(
              "fail to settle e meter due to serious problem: deviceId invalid or op empty id: {}",
              id);
          return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "设备UUID缺失或供应商非法");
        }
        ReturnDataInfo<EMeterCheckResponse> responseReturnDataInfo = processUpdatedEMeterConsumeInfo(
            opE.get().getElectronicRequestOp(), deviceId, meterInfo, id,
            eleMeterRequest.getUserName(), eleMeterRequest.getUserId());
        if (responseReturnDataInfo.isSuccess()) {
          EMeterCheckResponse checkResponse = responseReturnDataInfo.getResponseData();
          settlementDTO.setConsumeAmount(checkResponse.getConsumeAmount())
              .setConsumeRecordTime(checkResponse.getRecordTime());
          return ReturnDataInfo.successData(settlementDTO);
        }
        return ReturnDataInfo.failData(responseReturnDataInfo.getErrorInfo());
      } else {
        settlementDTO.setConsumeAmount(meterInfo.getConsumeamount())
            .setConsumeRecordTime(recordTime);
        return ReturnDataInfo.successData(settlementDTO);
      }
    } catch (Exception e) {
      log.error("fail to settle e meter due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 获取电表读数（非回调)-并且刷新数据库相关字段（读数、读数时间）
   *
   * @param eleMeterRequest userName, userId, 设备ID（业务内）非第三方
   * @return 通用返回结构包装的电表读数（recordTime为该读数记录时间）
   */
  @Transactional
  public ReturnDataInfo<EMeterCheckResponse> readAndSyncElectronicMeter(
      RequestDataInfo<Long> eleMeterRequest) {
    if (!eleMeterRequest.isValid()) {
      log.error(
          "fail to read and sync ele meter due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    long id = eleMeterRequest.getData();
    try {
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoForUpdate(id);
      if (meterInfo == null) {
        log.error("fail to find e meter info by id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }

      String deviceId = meterInfo.getDeviceid();
      Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(meterInfo.getProviderop());
      if (Common.isStringInValid(deviceId) || opE.isEmpty()) {
        log.error("fail to read meter due to serious problem: deviceId invalid or op empty id: {}",
            id);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "设备UUID缺失或供应商非法");
      }
      return processUpdatedEMeterConsumeInfo(opE.get().getElectronicRequestOp(), deviceId,
          meterInfo, id, eleMeterRequest.getUserName(), eleMeterRequest.getUserId());
    } catch (Exception e) {
      log.error("fail to read e meter due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 按业务内设备主键IDs查找电表信息
   *
   * @param ids 主键集合
   * @return 电表信息列表
   */
  public ReturnDataInfo<List<EMeterInfoDTO>> findEMeterInfoByIds(List<Long> ids) {
    if (Common.isCollectionInValid(ids)) {
      log.error("find e meter info failed due to ids invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<ElectronicMeterInfo> meterInfos = electronicMeterRepository.findEMeterInfoByIds(ids);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find e meter info failed due to result invalid for input ids:{}", ids);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }

    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按业务内设备主键ID查找电表信息
   *
   * @param id 主键
   * @return 电表信息
   */
  public ReturnDataInfo<EMeterInfoDTO> findEMeterInfoById(long id) {
    ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoById(id);
    if (meterInfo == null) {
      log.error("find e meter info failed due to no match result for input id:{}", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(toDTO(meterInfo));
  }

  /**
   * 按绑定房间ID查找房间相关电表列表
   *
   * @param roomIds 绑定的房源ID
   * @return 电表信息列表
   */
  public ReturnDataInfo<List<EMeterInfoDTO>> findEMeterInfoByRoomIds(List<String> roomIds) {
    if (Common.isCollectionInValid(roomIds)) {
      log.error("find e meter info failed due to roomIds invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<ElectronicMeterInfo> meterInfos = electronicMeterRepository.findEMeterInfoByRoomIds(
        roomIds);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find e meter info failed due to result invalid for input roomIds:{}", roomIds);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按绑定房源ID查找房源对应电表信息
   *
   * @param roomId 房源ID
   * @return 对应的电表信息
   */
  public ReturnDataInfo<List<EMeterInfoDTO>> findEMeterInfoByRoomId(String roomId) {
    List<ElectronicMeterInfo> meterInfos = electronicMeterRepository.findEMeterInfoByRoomId(roomId);
    if (Common.isCollectionInValid(meterInfos)) {
      log.error("find e meter info failed due to result invalid for input roomId:{}", roomId);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        meterInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 获取电表时间段内的用量读数-数据库任务描述待补充
   *
   * @param meterRequest 其中id与起止时间必填
   * @return 通用返回结构包装的开始时间至结束时间读数（具体读数时间段由返回中的start和end时间为准，与请求参数中的时间可能有误差）
   */
  public ReturnDataInfo<EMeterPeriodResponse> findElectronicUsagePeriod(MeterRequest meterRequest) {
    if (meterRequest == null) {
      log.error("fail to read period ele meter due to request input illegal! meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    long id = meterRequest.getId();
    try {
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoById(id);
      if (meterInfo == null) {
        log.error("fail to read period e meter info due to mismatch info by id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      String deviceId = meterInfo.getDeviceid();
      if (Common.isStringInValid(deviceId)) {
        log.error("fail to read period e meter due to serious problem: deviceId invalid for id: {}",
            id);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "设备UUID缺失");
      }
      int providerIndex = meterInfo.getProviderop();
      Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(providerIndex);
      if (opE.isEmpty()) {
        log.error("fail to read period e meter info due to op:{} type invalid", providerIndex);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      ElectronicRequestOp requestOp = opE.get().getElectronicRequestOp();
      EMeterPeriodResponse periodResponse = requestOp.findUsagePeriod(deviceId,
          meterRequest.getStartTime(), meterRequest.getEndTime());
      if (periodResponse.isSuccess()) {
        return ReturnDataInfo.successData(periodResponse);
      }
      return ReturnDataInfo.failData(periodResponse.getErrorInfo());
    } catch (Exception e) {
      log.error("fail to read period e meter due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 删除电表（无状态校验）
   *
   * @param delRequestForce 其中userName、userId必填，Long为电表主键id
   * @return 是否删除成功
   */
  public ReturnInfo delEMeter(RequestDataInfo<Long> delRequestForce) {
    if (!delRequestForce.isValid()) {
      log.error(
          "fail to adjust ele meter due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    long id = delRequestForce.getData();
    try {
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findEMeterInfoById(id);
      if (meterInfo == null) {
        log.error("fail to del e meter info due to mismatch info by id: {}", id);
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      meterInfo.setIsdeleted(2);
      meterInfo.setDeleteuserid(delRequestForce.getUserId());
      meterInfo.setDeleteusername(delRequestForce.getUserName());
      meterInfo.setDeletetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      electronicMeterRepository.update(meterInfo);
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to delete e meter due to db error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 开关电表操作可能是延迟2秒查询成功（开关已经完成），也有可能仍然处于操作阶段（未在2秒内完成开关，需依赖第三方的回调确认）,同一个表的闸门
   * 不可以并发操作，并发操作则返回系统错误，详见错误信息。当闸门处于打开中或关闭中的中间状态时，再次发起打开或关闭也返回系统错误，详见错误信息
   *
   * @param adjustRequest 请求结构，request里有userId，userName,meter里有id（业务内主键），provideOp:第三方枚举，云丁/合一、
   *                      serviceType：1打开、2关闭
   * @return fail的话见错误种类，success的话，有几种可能：1为已经关闭，2为已经打开，3为关闭中，4为打开中，-1为电表异常。
   * 例：假设发起的是打开请求，回复2表示已经打开，回复4表示未实时完成，后续依赖手动刷新
   */
  public ReturnDataInfo<Integer> adjustESwitch(RequestDataInfo<MeterRequest> adjustRequest) {
    MeterRequest meterRequest = adjustRequest.getData();
    if (!adjustRequest.isValid() || meterRequest == null) {
      log.error(
          "fail to adjust ele meter due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    long id = meterRequest.getId();
    int serviceType = meterRequest.getServiceType();
    try {
      ElectronicMeterInfo meterInfo = electronicMeterRepository.findById(id);
      if (meterInfo == null) {
        log.error("fail to adjust e meter info by id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      if (serviceType == meterInfo.getSwitchstatus()) {
        log.warn("e meter:{} is already in target status, no changes needed!", id);
        return ReturnDataInfo.successData(serviceType);
      }
      String deviceId = meterInfo.getDeviceid();
      if (Common.isStringInValid(deviceId)) {
        log.error("fail to adjust meter due to serious problem: deviceId invalid for id: {}", id);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "设备UUID缺失");
      }
      Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(meterInfo.getProviderop());
      Optional<EMeterSwitchResponse.SwitchTypeE> switchTypeE = EMeterSwitchResponse.SwitchTypeE.findTypeByIndex(
          serviceType);
      if (opE.isEmpty() || switchTypeE.isEmpty()) {
        log.error("fail to adjust ele meter due to request input mismatch!: opE/switchE invalid");
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
      EMeterSwitchResponse.SwitchTypeE switchType = switchTypeE.get();
      ElectronicRequestOp requestOp = opE.get().getElectronicRequestOp();
      return requestOp.adjustSwitch(switchType, switchProcessor, adjustRequest.getUserName(),
          adjustRequest.getUserId(), meterInfo);
    } catch (Exception e) {
      log.error("fail to adjust e meter due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  /**
   * 新增电表设备，执行时会调用第三方接口获取当前电表各项指数与状态，之后新增至服务内数据库，请求必要数据详见接口处使用OP区分服务商
   *
   * @param eleMeterRequest 服务请求，用户名，id以及必要的DTO数据
   * @return 是否成功，或错误信息
   */
  public ReturnInfo addNewElectronicMeter(RequestDataInfo<EMeterInfoDTO> eleMeterRequest) {
    EMeterInfoDTO EMeterInfoDTO = eleMeterRequest.getData();
    if (!eleMeterRequest.isValid() || EMeterInfoDTO == null) {
      log.error("fail to add new ele meter due to request input illegal!");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    int provideOp = EMeterInfoDTO.getProviderOp();
    Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(provideOp);
    if (opE.isEmpty()) {
      log.error("fail to add new ele meter due to illegal op");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    ProviderOpE provider = opE.get();
    NewElectronicMeterProcessor processor = provider.getNewElectronicMeterProcessor();
    ElectronicRequestOp requestOp = provider.getElectronicRequestOp();
    ElectronicMeterInfo electronicMeterInfo = new ElectronicMeterInfo();
    NewElectronicMeterProcessor.Processor eProcessor = processor.getProcessor();
    Optional<ErrorType> errorOp = eProcessor.parse(eleMeterRequest.getUserName(),
        eleMeterRequest.getUserId(),
        provider, electronicMeterInfo, EMeterInfoDTO);
    if (errorOp.isPresent()) {
      return ReturnInfo.failByType(errorOp.get());
    }
    EMeterCheckResponse checkResponse = requestOp.checkMeterFullState(
        electronicMeterInfo.getDeviceid());
    DeviceTask<EMeterCheckResponse, ReturnInfo> task = new DeviceTask<>(checkResponse) {
      @Override
      public boolean isTaskSuccess(EMeterCheckResponse responseData) {
        return responseData.isSuccess();
      }

      @Override
      public ReturnInfo successCallback(EMeterCheckResponse responseData) {
        eProcessor.process(responseData, electronicMeterInfo);
        try {
          electronicMeterRepository.insert(electronicMeterInfo);
          return ReturnInfo.success();
        } catch (Exception e) {
          log.error("fail to add new e meter due to db error: {}", e.getMessage());
          return ReturnInfo.failByType(ErrorType.DB_ERROR);
        }
      }

      @Override
      public ReturnInfo failCallback(EMeterCheckResponse responseData) {
        log.error("fail to add new e meter due to third part check fail!");
        ErrorInfo errorInfo = responseData.getErrorInfo();
        return ReturnInfo.fail(errorInfo);
      }
    };

    return task.runTask();
  }

  public Optional<ElectronicMeterInfo> findEMeterInfoByDeviceIdAndProvider(String deviceId,
      int provider, boolean needLock) {
    if (Common.isStringInValid(deviceId) || provider == 0) {
      log.error("find e meter info failed due to invalid device id or provider!");
      return Optional.empty();
    }
    ElectronicMeterInfo electronicMeterInfo =
        needLock ? electronicMeterRepository.findEMeterInfoByDeviceIdAndProviderForUpdate(deviceId,
            provider)
            : electronicMeterRepository.findEMeterInfoByDeviceIdAndProvider(deviceId, provider);
    if (electronicMeterInfo == null) {
      log.error("find e meter info failed due to no match e meter info!");
      return Optional.empty();
    }
    return Optional.of(electronicMeterInfo);
  }

  public boolean updateMeterConsumeInfo(ElectronicMeterInfo meterInfo, double consume,
      String recordTime, String userName, String userId) {
    long id = meterInfo.getId();
    double curConsume = meterInfo.getConsumeamount();
    String curRecordTime = meterInfo.getConsumerecordtime();
    if (recordTime.compareTo(curRecordTime) < 0 || Double.compare(consume, curConsume) < 0) {
      log.warn(
          "fail to update e meter due to abnormal param curConsume: {}, read: {}, curT: {}, read: {}, id: {}",
          curConsume, consume, curRecordTime, recordTime, id);
      return false;
    }
    meterInfo.setConsumeamount(consume);
    meterInfo.setConsumerecordtime(recordTime);
    meterInfo.setUpdateusername(userName);
    meterInfo.setUpdateuserid(userId);
    meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    electronicMeterRepository.update(meterInfo);
    return true;
  }

  public int syncEMeterOnOffLine(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return electronicMeterRepository.updateOnlineStatus(status, time, userName, userId, op,
        deviceId);
  }

  private ReturnDataInfo<EMeterCheckResponse> processUpdatedEMeterConsumeInfo(
      ElectronicRequestOp requestOp, String deviceId, ElectronicMeterInfo meterInfo, long id,
      String userName, String userId) {
    EMeterCheckResponse checkResponse = requestOp.readMeterRequest(deviceId);
    DeviceTask<EMeterCheckResponse, ReturnDataInfo<EMeterCheckResponse>> task = new DeviceTask<>(
        checkResponse) {
      @Override
      public boolean isTaskSuccess(EMeterCheckResponse responseData) {
        return responseData.isSuccess();
      }

      @Override
      public ReturnDataInfo<EMeterCheckResponse> successCallback(EMeterCheckResponse responseData) {
        if (updateMeterConsumeInfo(meterInfo, responseData.getConsumeAmount(),
            responseData.getRecordTime(), userName, userId)) {
          return ReturnDataInfo.successData(responseData);
        }
        return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR,
            String.format("电表数值异常或抄表时间异常id: %s, 请检查数据", id));
      }

      @Override
      public ReturnDataInfo<EMeterCheckResponse> failCallback(EMeterCheckResponse responseData) {
        ErrorInfo errorInfo = responseData.getErrorInfo();
        log.error("fail to read mater due to third party error: {}, id: {}",
            errorInfo.getErrorMsg(), id);
        return ReturnDataInfo.failData(errorInfo);
      }
    };
    return task.runTask();
  }

  private Optional<Boolean> needCheckForUpdate(String recordTime) {
    Optional<String> validToTimeOp = Common.findSysTimeAfter(recordTime, 60);
    if (validToTimeOp.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(validToTimeOp.get()
        .compareTo(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis())) < 0);
  }

  private void buildAndPutSuccessSettleEMeter(Map<Boolean, Set<MeterSettlementDTO>> results,
      long id, double consume,
      String recordT, String startTp, double consumeP) {
    Set<MeterSettlementDTO> successSet = results.computeIfAbsent(true, k -> new HashSet<>());
    successSet.add(genMeterSettlementSuccessDTO(id, consume, recordT, startTp, consumeP));
  }

  private MeterSettlementDTO genMeterSettlementSuccessDTO(long id, double consume, String recordT,
      String startTp, double consumeP) {
    MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
    settlementDTO.setId(id)
        .setConsumeAmount(consume)
        .setConsumeRecordTime(recordT)
        .setPeriodConsumeAmount(consumeP)
        .setPeriodConsumeStartTime(startTp)
        .setReturnInfo(ReturnInfo.success());
    return settlementDTO;
  }

  /**
   * 把数据库查到的有效的电表数据进行分类切割，数据有问题的分类到提交失败，没有问题的，检查有效时间（60分钟）内的直接返回当前值，超过有效时间，
   * 则需要调用第三方接口，对于需要要调用第三方接口的，做分类批处理，provider指定了服务商，同一个服务商下会有多个batch（并发），每个batch的 大小由BATCH_SIZE指定。
   *
   * @param fullDataInfoBatch      数据库中存在的数据
   * @param results                最终返回的结果
   * @param needUpdateDevicesBatch 需要调用第三方的批次容器，key区分服务商，List中为每次并发请求的batch内容（deviceIds批次封装）
   */
  private void separateAndAllocateEMeterSettleFromDBBatch(
      Map<Long, ElectronicMeterInfo> fullDataInfoBatch,
      Map<Boolean, Set<MeterSettlementDTO>> results,
      Map<ProviderOpE, List<Set<Pair<Long, String>>>> needUpdateDevicesBatch) {
    fullDataInfoBatch.forEach((id, info) -> {
      String recordTime = info.getConsumerecordtime();
      Optional<Boolean> needCheckAndUpdate = needCheckForUpdate(recordTime);
      if (needCheckAndUpdate.isEmpty()) {
        buildAndPutFailSettleEMeter(results,
            ReturnInfo.failByType(ErrorType.SERIOUS_ERROR, "电表抄表时间格式错误"), id);
      } else {
        if (needCheckAndUpdate.get()) {
          Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(info.getProviderop());
          String deviceId = info.getDeviceid();
          if (opE.isPresent() && !Common.isStringInValid(deviceId)) {
            List<Set<Pair<Long, String>>> opIdsBatch = needUpdateDevicesBatch.computeIfAbsent(
                opE.get(), k -> new ArrayList<>());
            if (opIdsBatch.isEmpty()) {
              Set<Pair<Long, String>> opIdsNew = new HashSet<>();
              opIdsNew.add(Pair.of(id, deviceId));
              opIdsBatch.add(opIdsNew);
            } else {
              Set<Pair<Long, String>> opIds = opIdsBatch.get(opIdsBatch.size() - 1);
              if (opIds.size() < BATCH_SIZE) {
                opIds.add(Pair.of(id, deviceId));
              } else {
                Set<Pair<Long, String>> opIdsNew = new HashSet<>();
                opIdsNew.add(Pair.of(id, deviceId));
                opIdsBatch.add(opIdsNew);
              }
            }
          } else {
            buildAndPutFailSettleEMeter(results,
                ReturnInfo.failByType(ErrorType.SERIOUS_ERROR, "库中供应商非法或deviceId无效"), id);
          }
        } else {
          buildAndPutSuccessSettleEMeter(results, id, info.getConsumeamount(), recordTime,
              info.getPeriodconsumestarttime(), info.getPeriodconsumeamount());
        }
      }

    });
  }

  private Map<Long, EMeterCheckResponse> runNeedUpdateDeviceBatchTask(
      Map<ProviderOpE, List<Set<Pair<Long, String>>>> needUpdateDevicesBatch)
      throws ExecutionException, InterruptedException, TimeoutException {
    List<CompletableFuture<Map.Entry<ProviderOpE, Map<Long, EMeterCheckResponse>>>> futures =
        needUpdateDevicesBatch.entrySet().stream()
            .map(entry -> CompletableFuture.supplyAsync(() -> {
              ProviderOpE opE = entry.getKey();
              List<Set<Pair<Long, String>>> opBatch = entry.getValue();
              ElectronicRequestOp requestOp = opE.getElectronicRequestOp();
              Map<Long, EMeterCheckResponse> checkResponses = new HashMap<>();
              opBatch.forEach(tasks -> {
                tasks.forEach(devicePair -> {
                  EMeterCheckResponse checkResponse = requestOp.readMeterRequest(
                      devicePair.getSecond());
                  checkResponses.put(devicePair.getFirst(), checkResponse);
                });
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  log.error(
                      "read e meter batch waiting is interrupted, result maybe not finished yet!");
                  Thread.currentThread().interrupt();
                }
              });
              return Map.entry(opE, checkResponses);
            })).collect(Collectors.toList());

    CompletableFuture<Void> allDone = CompletableFuture.allOf(
        futures.toArray(new CompletableFuture[0]));

    CompletableFuture<Map<Long, EMeterCheckResponse>> resultFuture = allDone.thenApply(
        v -> futures.stream()
            .map(CompletableFuture::join)
            .map(Map.Entry::getValue)
            .flatMap(map -> map.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    return resultFuture.get(60, TimeUnit.SECONDS);
  }

  private void separateAndAllocateEMeterSettleFromBatchTaskResult(
      Map<Long, EMeterCheckResponse> readResults,
      Set<Long> idsShouldUpdate, Map<Boolean, Set<MeterSettlementDTO>> results) {
    readResults.forEach((k, v) -> {
      if (v.isSuccess()) {
        idsShouldUpdate.add(k);
      } else {
        buildAndPutFailSettleEMeter(results, ReturnInfo.fail(v.getErrorInfo()), k);
      }
    });
  }

  private void buildAndPutFailSettleEMeter(Map<Boolean, Set<MeterSettlementDTO>> results,
      ReturnInfo returnInfo, long id) {
    MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
    settlementDTO.setId(id);
    settlementDTO.setReturnInfo(returnInfo);
    Set<MeterSettlementDTO> failSet = results.computeIfAbsent(false, k -> new HashSet<>());
    failSet.add(settlementDTO);
  }

  private void filterDBMismatchResults(Map<Long, ElectronicMeterInfo> dbResult,
      Map<Boolean, Set<MeterSettlementDTO>> results, Set<Long> requestIds) {
    if (dbResult.size() != requestIds.size()) {
      for (long id : requestIds) {
        if (!dbResult.containsKey(id)) {
          buildAndPutFailSettleEMeter(results,
              ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "id在数据库中不存在"), id);
        }
      }
    }
  }

  private void separateShouldUpdateEMeterAndHasBeenUpdated(
      Map<Long, ElectronicMeterInfo> dbBeforeUpdateResults,
      Map<Long, EMeterCheckResponse> readResults, Map<Boolean, Set<MeterSettlementDTO>> results,
      Set<MeterSettlementDTO> batchUpdateSet) {
    dbBeforeUpdateResults.forEach((id, info) -> {
      EMeterCheckResponse checkResponse = readResults.get(id);
      double checkConsume = checkResponse.getConsumeAmount();
      String checkTime = checkResponse.getRecordTime();
      double consumeDb = info.getConsumeamount();
      String checkTimeDb = info.getConsumerecordtime();
      if (Common.isStringInValid(checkTime) || Common.isStringInValid(checkTimeDb)) {
        buildAndPutFailSettleEMeter(results,
            ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "记录时间格式无效：第三方或DB"),
            id);
      } else {
        String startTimeP = info.getPeriodconsumestarttime();
        double consumeP = info.getPeriodconsumeamount();
        if (checkTimeDb.compareTo(checkTime) > 0) {
          buildAndPutSuccessSettleEMeter(results, id, consumeDb, checkTimeDb, startTimeP, consumeP);
        } else {
          batchUpdateSet.add(
              genMeterSettlementSuccessDTO(id, checkConsume, checkTime, startTimeP, consumeP));
          buildAndPutSuccessSettleEMeter(results, id, checkConsume, checkTime, startTimeP,
              consumeP);
        }
      }
    });
  }

  private ReturnInfo asyncEMeterSettleSuccessNotifyInfoToMeterInfo(MeterSettlementDTO settlementDTO,
      ElectronicMeterInfo meterInfo,
      String userName, String userId) {
    double consume = settlementDTO.getConsumeAmount();
    String recordT = settlementDTO.getConsumeRecordTime();
    double pConsume = settlementDTO.getPeriodConsumeAmount();
    String recordTP = settlementDTO.getPeriodConsumeStartTime();
    if (Double.compare(consume, 0.0) < 0 || Double.compare(pConsume, 0.0) < 0
        || Common.isStringInValid(recordT)
        || Common.isStringInValid(recordTP) || recordT.compareTo(recordTP) < 0
        || Double.compare(consume, pConsume) < 0) {
      log.error("fail to notify e settlement due to dto params illegal, id: {}", meterInfo.getId());
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }

    double pConsumeN = meterInfo.getPeriodconsumeamount();
    String recordTPN = meterInfo.getPeriodconsumestarttime();
    if (Double.compare(pConsume, pConsumeN) != 0 || Common.isStringInValid(recordTPN)
        || recordTPN.compareTo(recordTP) != 0) {
      log.error(
          "fail to notify e settlement e due to data abnormal problem: recordTime format is invalid, id: {}",
          meterInfo.getId());
      return ReturnInfo.failByType(ErrorType.DATA_ABNORMAL_ERROR, "抄表记录时间或读数对照异常");
    }
    meterInfo.setPeriodconsumeamount(consume);
    meterInfo.setPeriodconsumestarttime(recordT);
    meterInfo.setUpdateusername(userName);
    meterInfo.setUpdateuserid(userId);
    meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    return ReturnInfo.success();
  }

  private EMeterInfoDTO toDTO(ElectronicMeterInfo electronicMeterInfo) {
    EMeterInfoDTO infoDTO = new EMeterInfoDTO();
    infoDTO.setSwitchStatus(electronicMeterInfo.getSwitchstatus());
    infoDTO.setEnableStateTime(electronicMeterInfo.getEnablestatetime());
    infoDTO.setLastCommTime(electronicMeterInfo.getEnablestatetime());
    infoDTO.setId(electronicMeterInfo.getId())
        .setInstallType(electronicMeterInfo.getInstalltype())
        .setMeterNo(electronicMeterInfo.getMeterno())
        .setBoundRoomId(electronicMeterInfo.getBoundroomid())
        .setBoundTimeChange(electronicMeterInfo.getBoundtimechange())
        .setConsumeAmount(electronicMeterInfo.getConsumeamount())
        .setConsumeRecordTime(electronicMeterInfo.getConsumerecordtime())
        .setPeriodConsumeStartTime(electronicMeterInfo.getPeriodconsumestarttime())
        .setPeriodConsumeAmount(electronicMeterInfo.getPeriodconsumeamount())
        .setStatus(electronicMeterInfo.getStatus())
        .setStatusTime(electronicMeterInfo.getStatustime())
        .setDeviceId(electronicMeterInfo.getDeviceid())
        .setDeviceModelId(electronicMeterInfo.getDevicemodelid())
        .setProviderName(electronicMeterInfo.getProvidername())
        .setProviderOp(electronicMeterInfo.getProviderop())
        .setCollectorId(electronicMeterInfo.getCollectorid())
        .setCreateUserId(electronicMeterInfo.getCreateuserid())
        .setCreateUserName(electronicMeterInfo.getCreateusername())
        .setCreateTime(electronicMeterInfo.getCreatetime())
        .setUpdateUserId(electronicMeterInfo.getUpdateuserid())
        .setUpdateUserName(electronicMeterInfo.getUpdateusername())
        .setUpdateTime(electronicMeterInfo.getUpdatetime())
        .setIsDeleted(electronicMeterInfo.getIsdeleted())
        .setDeleteUserId(electronicMeterInfo.getDeleteuserid())
        .setDeleteUserName(electronicMeterInfo.getDeleteusername())
        .setDeleteTime(electronicMeterInfo.getDeletetime());
    return infoDTO;
  }
}
