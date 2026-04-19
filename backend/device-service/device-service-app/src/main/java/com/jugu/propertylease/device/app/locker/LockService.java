package com.jugu.propertylease.device.app.locker;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.lock.LockCheckResponse;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.enums.ServiceTypeE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.LockInfo;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.repository.LockRepository;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.common.entity.dto.LockInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.request.LockPwdOpRequest;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
public class LockService {

  public static final int LOW_BATTERY = 15;

  private final LockRepository lockRepository;

  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  public ReturnInfo addNewLock(RequestDataInfo<LockInfoDTO> lockRequest) {
    if (lockRequest.isValid()) {
      LockInfoDTO lockInfoDTO = lockRequest.getData();
      if (lockInfoDTO != null) {
        String deviceId = lockInfoDTO.getDeviceId();
        if (deviceId == null) {
          log.error("add new lock fail due to deviceId invalid!");
          return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
        }
        int opIndex = lockInfoDTO.getProviderOp();
        Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(opIndex);
        if (opE.isEmpty()) {
          log.error("add new lock fail due to op:{} invalid!", opE);
          return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
        }
        ProviderOpE providerOpE = opE.get();
        LockCheckResponse checkResponse = providerOpE.getLockRequestOp().findLockInfo(deviceId);
        if (checkResponse.isSuccess()) {
          setNewLock(lockInfoDTO, checkResponse, providerOpE.getName(), lockRequest.getUserName(),
              lockRequest.getUserId());
          try {
            lockRepository.addNewLock(lockInfoDTO);
            return ReturnInfo.success();
          } catch (Exception e) {
            log.error("fail to add new lock due to db error: {}", e.getMessage());
            return ReturnInfo.failByType(ErrorType.DB_ERROR);
          }
        }
        return ReturnInfo.fail(checkResponse.getErrorInfo());
      }
    }
    log.error("add new lock fail due to request invalid!");
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  public ReturnInfo adjustBoundRoom(RequestDataInfo<LockInfoDTO> adjustRequest) {
    if (!adjustRequest.isValid()) {
      log.error(
          "fail to adjust lock room due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    LockInfoDTO dto = adjustRequest.getData();
    if (dto == null) {
      log.error("fail to adjust lock room due to dto invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      lockRepository.updateBoundRoom(dto.getId(), dto.getBoundRoomId(), adjustRequest.getUserName(),
          adjustRequest.getUserId(),
          Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to adjust lock room due to ab error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  public ReturnDataInfo<AddLockPwdResponse> addLockPwd(AddLockPwdRequest addLockPwdRequest) {
    if (addLockPwdRequest.isValid()) {
      Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(
          addLockPwdRequest.getProviderOp());
      if (opE.isPresent()) {
        ProviderOpE providerOpE = opE.get();
        ThirdPartyServiceTemp serviceTemp = new ThirdPartyServiceTemp();
        serviceTemp.setDeviceid(addLockPwdRequest.getDeviceId());
        serviceTemp.setProviderop(providerOpE.getIndex());
        serviceTemp.setDevicetype(ThirdPartyServiceRecordMgr.DeviceType.LOCKER.getIndex());
        serviceTemp.setServicetype(ServiceTypeE.ADD.getIndex());
        serviceTemp.setServicesttime(
            Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
        serviceTemp.setServiceresult(
            ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex());
        AddLockPwdResponse response = providerOpE.getLockRequestOp().addLockPwd(addLockPwdRequest);
        if (response.isSuccess()) {
          try {
            serviceTemp.setServiceid(response.getServiceId());
            serviceTemp.setServicenote(response.getServiceNote());
            serviceTemp.setAdditionalinfo(response.getPasswordId());
            long serviceKey = thirdPartyServiceRecordMgr.insertRecord(serviceTemp);
            response.setServiceKey(serviceKey);
            return ReturnDataInfo.successData(response);
          } catch (Exception e) {
            log.error("fail to add lock pwd due to ab error: {}", e.getMessage());
            return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
          }
        }
        return ReturnDataInfo.failData(response.getErrorInfo());
      }
    }
    log.error("fail to add lock pwd due to request param invalid");
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  public ReturnDataInfo<LockPwdsSummary> findLockPwdSummary(long id) {
    try {
      LockInfo lockInfo = lockRepository.findLockInfoById(id);
      if (lockInfo != null) {
        int op = lockInfo.getProviderop();
        String deviceId = lockInfo.getDeviceid();
        Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(op);
        if (opE.isPresent() && !Common.isStringInValid(deviceId)) {
          LockPwdsSummary lockPwdsSummary = opE.get().getLockRequestOp()
              .checkLockPwdInfos(deviceId);
          if (lockPwdsSummary.isSuccess()) {
            return ReturnDataInfo.successData(lockPwdsSummary);
          }
          return ReturnDataInfo.failData(lockPwdsSummary.getErrorInfo());
        }
        log.error("fail to find lock pwd summary due to op:{} or deviceId:{} invalid", op,
            deviceId);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "op或deviceId无效/缺失");
      }
      log.error("fail to find lock pwd summary due to id:{} invalid", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    } catch (Exception e) {
      log.error("fail to find lock pwd summary due to ab error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  public ReturnDataInfo<ServiceRecordDTO> delLockPwd(LockPwdOpRequest opRequest) {
    LockPwdOpI opI = (deviceId, pwdId, providerOpE) -> providerOpE.getLockRequestOp()
        .delLockPwd(deviceId, pwdId);
    return processLockPwdOp(opRequest, opI, ServiceTypeE.DEL);
  }

  public ReturnDataInfo<ServiceRecordDTO> frozenLockPwd(LockPwdOpRequest opRequest) {
    LockPwdOpI opI = (deviceId, pwdId, providerOpE) -> providerOpE.getLockRequestOp()
        .frozenLockPwd(deviceId, pwdId);
    return processLockPwdOp(opRequest, opI, ServiceTypeE.FROZEN);
  }

  public ReturnDataInfo<ServiceRecordDTO> unfrozenLockPwd(LockPwdOpRequest opRequest) {
    LockPwdOpI opI = (deviceId, pwdId, providerOpE) -> providerOpE.getLockRequestOp()
        .unfrozenLockPwd(deviceId, pwdId);
    return processLockPwdOp(opRequest, opI, ServiceTypeE.UNFROZEN);
  }

  /**
   * 按业务内设备主键IDs查找智能锁信息
   *
   * @param ids 主键集合
   * @return 智能锁信息列表
   */
  public ReturnDataInfo<List<LockInfoDTO>> findLockInfoByIds(List<Long> ids) {
    if (Common.isCollectionInValid(ids)) {
      log.error("find lock info failed due to ids invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<LockInfo> lockInfos = lockRepository.findLockInfoByIds(ids);
    if (Common.isCollectionInValid(lockInfos)) {
      log.error("find lock info failed due to result invalid for input ids:{}", ids);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }

    return ReturnDataInfo.successData(
        lockInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按业务内设备主键ID查找智能锁信息
   *
   * @param id 主键
   * @return 智能锁信息
   */
  public ReturnDataInfo<LockInfoDTO> findLockInfoById(long id) {
    LockInfo lockInfo = lockRepository.findLockInfoById(id);
    if (lockInfo == null) {
      log.error("find lock info failed due to no match result for input id:{}", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(toDTO(lockInfo));
  }

  /**
   * 按绑定房间ID查找房间相关智能锁列表
   *
   * @param roomIds 绑定的房源ID
   * @return 智能锁信息列表
   */
  public ReturnDataInfo<List<LockInfoDTO>> findLockInfoByRoomIds(List<String> roomIds) {
    if (Common.isCollectionInValid(roomIds)) {
      log.error("find lock info failed due to roomIds invalid");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    List<LockInfo> lockInfos = lockRepository.findLockInfoByRoomIds(roomIds);
    if (Common.isCollectionInValid(lockInfos)) {
      log.error("find lock info failed due to result invalid for input roomIds:{}", roomIds);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        lockInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 按绑定房源ID查找房源对应智能锁表信息
   *
   * @param roomId 房源ID
   * @return 对应的智能锁信息
   */
  public ReturnDataInfo<List<LockInfoDTO>> findLockInfoByRoomId(String roomId) {
    List<LockInfo> lockInfos = lockRepository.findLockInfoByRoomId(roomId);
    if (Common.isCollectionInValid(lockInfos)) {
      log.error("find lock info failed due to result invalid for input roomId:{}", roomId);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    return ReturnDataInfo.successData(
        lockInfos.stream().map(this::toDTO).collect(Collectors.toList()));
  }

  /**
   * 删除智能锁（无状态校验）
   *
   * @param delRequestForce 其中userName、userId必填，Long为智能锁主键id
   * @return 是否删除成功
   */
  public ReturnInfo delLock(RequestDataInfo<Long> delRequestForce) {
    if (!delRequestForce.isValid()) {
      log.error(
          "fail to del lock due to request input illegal!:userId/userName/meterRequest invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    long id = delRequestForce.getData();
    try {
      LockInfo lockInfo = lockRepository.findLockInfoById(id);
      if (lockInfo == null) {
        log.error("fail to del lock due to mismatch info by id: {}", id);
        return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
      }
      lockInfo.setIsdeleted(2);
      lockInfo.setDeleteuserid(delRequestForce.getUserId());
      lockInfo.setDeleteusername(delRequestForce.getUserName());
      lockInfo.setDeletetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      lockRepository.update(lockInfo);
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail to del lock due to db error: {}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  public void syncLockElectricity(String deviceId, int providerOp, int electricity, String userName,
      String userId) {
    lockRepository.updateElectricityByOpAndDeviceId(providerOp, deviceId, electricity, userName,
        userId);
  }

  public int syncLockOnOffLine(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return lockRepository.updateOnlineStatus(status, time, userName, userId, op, deviceId);
  }

  public Optional<LockInfo> findLockInfoByDeviceIdAndProvider(String deviceId, int provider) {
    if (Common.isStringInValid(deviceId) || provider == 0) {
      log.error("find lock info failed due to invalid device id or provider!");
      return Optional.empty();
    }
    LockInfo lockInfo = lockRepository.findLockInfoByDeviceIdAndProvider(deviceId, provider);
    if (lockInfo == null) {
      log.error("find lock info failed due to not match!");
      return Optional.empty();
    }
    return Optional.of(lockInfo);
  }

  private ReturnDataInfo<ServiceRecordDTO> processLockPwdOp(LockPwdOpRequest opRequest,
      LockPwdOpI pwdOpI, ServiceTypeE serviceTypeE) {
    String pwdId = opRequest.getPwdId();
    if (Common.isStringInValid(pwdId)) {
      log.error("lock pwd op failed due to pwd id error!");
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    try {
      long id = opRequest.getId();
      LockInfo lockInfo = lockRepository.findLockInfoById(id);
      if (lockInfo != null) {
        String deviceId = lockInfo.getDeviceid();
        int op = lockInfo.getProviderop();
        Optional<ProviderOpE> opE = ProviderOpE.findProviderByIndex(op);
        if (!Common.isStringInValid(deviceId) && opE.isPresent()) {
          ThirdPartyServiceTemp serviceTemp = new ThirdPartyServiceTemp();
          serviceTemp.setDeviceid(deviceId);
          serviceTemp.setProviderop(op);
          serviceTemp.setDevicetype(ThirdPartyServiceRecordMgr.DeviceType.LOCKER.getIndex());
          serviceTemp.setServicetype(serviceTypeE.getIndex());
          serviceTemp.setServicesttime(
              Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          serviceTemp.setServiceresult(
              ThirdPartyServiceRecordMgr.ServiceResult.IN_PROGRESSING.getIndex());
          DeviceResponse deviceResponse = pwdOpI.runOp(deviceId, pwdId, opE.get());
          if (deviceResponse.isSuccess()) {
            serviceTemp.setServiceid(deviceResponse.getServiceId());
            serviceTemp.setServicenote(deviceResponse.getServiceNote());
            serviceTemp.setAdditionalinfo(pwdId);
            long serviceKey = thirdPartyServiceRecordMgr.insertRecord(serviceTemp);
            return ReturnDataInfo.successData(new ServiceRecordDTO(serviceKey, op, deviceId, true));
          }
          return ReturnDataInfo.failData(deviceResponse.getErrorInfo());
        }
        log.error("lock pwd op failed due to op:{} or deviceId:{} invalid", op, deviceId);
        return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "op或deviceId无效/缺失");
      }
      log.error("lock pwd op failed due to lock id:{} not match!", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    } catch (Exception e) {
      log.error("lock pwd op failed due to ab error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  private void setNewLock(LockInfoDTO lockInfoDTO, LockCheckResponse checkResponse, String opName,
      String userName, String userId) {
    int ele = checkResponse.getElectricity();
    lockInfoDTO.setName(checkResponse.getName())
        .setMac(checkResponse.getMac())
        .setSn(checkResponse.getSn())
        .setProductModel(checkResponse.getProductModel())
        .setOnlineStatus(checkResponse.getOnlineStatus())
        .setOnlineStatusTime(checkResponse.getOnlineStatusTime())
        .setLockSignal(checkResponse.getLockSignal())
        .setStatus(ele > LOW_BATTERY ? 1 : 2)
        .setElectricity(ele)
        .setProviderName(opName)
        .setCreateUserId(userId)
        .setCreateUserName(userName)
        .setCreateTime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
  }

  private LockInfoDTO toDTO(LockInfo entity) {
    if (entity == null) {
      return null;
    }
    LockInfoDTO dto = new LockInfoDTO();
    dto.setId(entity.getId() != null ? entity.getId() : 0L);
    dto.setBoundRoomId(entity.getBoundroomid());
    dto.setDeviceId(entity.getDeviceid());
    dto.setName(entity.getName());
    dto.setMac(entity.getMac());
    dto.setSn(entity.getSn());
    dto.setAuthKey(entity.getAuthkey());
    dto.setProductModel(entity.getProductmodel());
    dto.setHw(entity.getHw());
    dto.setStatus(entity.getStatus() != null ? entity.getStatus() : 0);
    dto.setOnlineStatus(entity.getOnlinestatus() != null ? entity.getOnlinestatus() : 0);
    dto.setOnlineStatusTime(entity.getOnlinestatustime());
    dto.setLockSignal(entity.getLocksignal() != null ? entity.getLocksignal() : 0);
    dto.setElectricity(entity.getElectricity() != null ? entity.getElectricity() : 0);
    dto.setCcid(entity.getCcid());
    dto.setSw(entity.getSw());
    dto.setProviderName(entity.getProvidername());
    dto.setProviderOp(entity.getProviderop() != null ? entity.getProviderop() : 0);
    dto.setCreateUserId(entity.getCreateuserid());
    dto.setCreateUserName(entity.getCreateusername());
    dto.setCreateTime(entity.getCreatetime());
    dto.setUpdateUserId(entity.getUpdateuserid());
    dto.setUpdateUserName(entity.getUpdateusername());
    dto.setUpdateTime(entity.getUpdatetime());
    dto.setIsDeleted(entity.getIsdeleted() != null ? entity.getIsdeleted() : 0);
    dto.setDeleteUserId(entity.getDeleteuserid());
    dto.setDeleteUserName(entity.getDeleteusername());
    dto.setDeleteTime(entity.getDeletetime());

    return dto;
  }
}
