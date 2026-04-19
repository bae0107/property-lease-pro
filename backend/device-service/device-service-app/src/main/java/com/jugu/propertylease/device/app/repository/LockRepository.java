package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.LockInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.LockInfo;
import com.jugu.propertylease.device.common.entity.dto.LockInfoDTO;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class LockRepository {

  private final DSLContext dslContext;

  private final LockInfoDao lockInfoDao;

  public void update(LockInfo lockInfo) {
    lockInfoDao.update(lockInfo);
  }

  public void addNewLock(LockInfoDTO lockInfoDTO) {
    LockInfo lockInfo = new LockInfo();
    lockInfo.setBoundroomid(lockInfoDTO.getBoundRoomId());
    lockInfo.setDeviceid(lockInfoDTO.getDeviceId());
    lockInfo.setName(lockInfoDTO.getName());
    lockInfo.setMac(lockInfoDTO.getMac());
    lockInfo.setSn(lockInfoDTO.getSn());
    lockInfo.setProductmodel(lockInfoDTO.getProductModel());
    lockInfo.setStatus(lockInfoDTO.getStatus());
    lockInfo.setOnlinestatus(lockInfoDTO.getOnlineStatus());
    lockInfo.setOnlinestatustime(lockInfoDTO.getOnlineStatusTime());
    lockInfo.setLocksignal(lockInfoDTO.getLockSignal());
    lockInfo.setElectricity(lockInfoDTO.getElectricity());
    lockInfo.setProvidername(lockInfoDTO.getProviderName());
    lockInfo.setProviderop(lockInfoDTO.getProviderOp());
    lockInfo.setCreatetime(lockInfoDTO.getCreateTime());
    lockInfo.setCreateuserid(lockInfoDTO.getCreateUserId());
    lockInfo.setCreateusername(lockInfoDTO.getCreateUserName());
    lockInfoDao.insert(lockInfo);
  }

  public LockInfo findLockInfoById(long id) {
    return dslContext.selectFrom(Tables.LOCK_INFO)
        .where(Tables.LOCK_INFO.ID.eq(id))
        .fetchOneInto(LockInfo.class);
  }

  public LockInfo findLockInfoByDeviceIdAndProvider(String deviceId, int provider) {
    return dslContext.selectFrom(Tables.LOCK_INFO)
        .where(Tables.LOCK_INFO.DEVICEID.eq(deviceId))
        .and(Tables.LOCK_INFO.PROVIDEROP.eq(provider))
        .fetchOneInto(LockInfo.class);
  }

  public void updateElectricityByOpAndDeviceId(int op, String deviceId, int electricity,
      String userName, String userId) {
    dslContext.update(Tables.LOCK_INFO)
        .set(Tables.LOCK_INFO.ELECTRICITY, electricity)
        .set(Tables.LOCK_INFO.UPDATEUSERNAME, userName)
        .set(Tables.LOCK_INFO.UPDATEUSERID, userId)
        .set(Tables.LOCK_INFO.UPDATETIME,
            Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()))
        .where(Tables.LOCK_INFO.PROVIDEROP.eq(op))
        .and(Tables.LOCK_INFO.DEVICEID.eq(deviceId))
        .execute();
  }

  public int updateOnlineStatus(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return dslContext.update(Tables.LOCK_INFO)
        .set(Tables.LOCK_INFO.ONLINESTATUS, status)
        .set(Tables.LOCK_INFO.ONLINESTATUSTIME, time)
        .set(Tables.LOCK_INFO.UPDATEUSERID, userId)
        .set(Tables.LOCK_INFO.UPDATEUSERNAME, userName)
        .set(Tables.LOCK_INFO.UPDATETIME, time)
        .where(Tables.LOCK_INFO.PROVIDEROP.eq(op))
        .and(Tables.LOCK_INFO.DEVICEID.eq(deviceId))
        .execute();
  }

  public void updateBoundRoom(long id, String roomId, String userName, String userId, String time) {
    dslContext.update(Tables.LOCK_INFO)
        .set(Tables.LOCK_INFO.BOUNDROOMID, roomId)
        .set(Tables.LOCK_INFO.UPDATEUSERID, userId)
        .set(Tables.LOCK_INFO.UPDATEUSERNAME, userName)
        .set(Tables.LOCK_INFO.UPDATETIME, time)
        .where(Tables.LOCK_INFO.ID.eq(id))
        .execute();
  }

  public List<LockInfo> findLockInfoByRoomIds(Collection<String> roomIds) {
    return dslContext.selectFrom(Tables.LOCK_INFO)
        .where(Tables.LOCK_INFO.BOUNDROOMID.in(roomIds))
        .fetchInto(LockInfo.class);
  }

  public List<LockInfo> findLockInfoByRoomId(String roomId) {
    return dslContext.selectFrom(Tables.LOCK_INFO)
        .where(Tables.LOCK_INFO.BOUNDROOMID.eq(roomId))
        .fetchInto(LockInfo.class);
  }

  public List<LockInfo> findLockInfoByIds(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.LOCK_INFO)
        .where(Tables.LOCK_INFO.ID.in(ids))
        .fetchInto(LockInfo.class);
  }
}
