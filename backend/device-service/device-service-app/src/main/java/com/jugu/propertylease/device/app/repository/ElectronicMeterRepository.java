package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.ElectronicMeterInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.stereotype.Repository;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class ElectronicMeterRepository {

  private final DSLContext dslContext;

  private final ElectronicMeterInfoDao electronicMeterInfoDao;

  public ElectronicMeterInfo findEMeterInfoForUpdate(long id) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.ID.eq(id))
        .forUpdate()
        .fetchOneInto(ElectronicMeterInfo.class);
  }

  public ElectronicMeterInfo findEMeterInfoByDeviceIdAndProvider(String deviceId, int provider) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.DEVICEID.eq(deviceId))
        .and(Tables.ELECTRONIC_METER_INFO.PROVIDEROP.eq(provider))
        .fetchOneInto(ElectronicMeterInfo.class);
  }

  public ElectronicMeterInfo findEMeterInfoByDeviceIdAndProviderForUpdate(String deviceId,
      int provider) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.DEVICEID.eq(deviceId))
        .and(Tables.ELECTRONIC_METER_INFO.PROVIDEROP.eq(provider))
        .forUpdate()
        .fetchOneInto(ElectronicMeterInfo.class);
  }

  public void updateEMeterReadInfoBatch(Set<MeterSettlementDTO> batchUpdateSet, String userName,
      String userId) {
    String timeCur = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    List<Query> queries = batchUpdateSet.stream()
        .map(dto -> dslContext.update(Tables.ELECTRONIC_METER_INFO)
            .set(Tables.ELECTRONIC_METER_INFO.CONSUMEAMOUNT, dto.getConsumeAmount())
            .set(Tables.ELECTRONIC_METER_INFO.CONSUMERECORDTIME, dto.getConsumeRecordTime())
            .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERID, userId)
            .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERNAME, userName)
            .set(Tables.ELECTRONIC_METER_INFO.UPDATETIME, timeCur)
            .where(Tables.ELECTRONIC_METER_INFO.ID.eq(dto.getId())))
        .collect(Collectors.toList());

    dslContext.batch(queries).execute();
  }

  public void updateBoundRoom(long id, String roomId, String userName, String userId, String time) {
    dslContext.update(Tables.ELECTRONIC_METER_INFO)
        .set(Tables.ELECTRONIC_METER_INFO.BOUNDROOMID, roomId)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERID, userId)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERNAME, userName)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATETIME, time)
        .where(Tables.ELECTRONIC_METER_INFO.ID.eq(id))
        .execute();
  }

  public int updateOnlineStatus(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return dslContext.update(Tables.ELECTRONIC_METER_INFO)
        .set(Tables.ELECTRONIC_METER_INFO.STATUS, status)
        .set(Tables.ELECTRONIC_METER_INFO.STATUSTIME, time)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERID, userId)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERNAME, userName)
        .set(Tables.ELECTRONIC_METER_INFO.UPDATETIME, time)
        .where(Tables.ELECTRONIC_METER_INFO.PROVIDEROP.eq(op))
        .and(Tables.ELECTRONIC_METER_INFO.DEVICEID.eq(deviceId))
        .execute();
  }

  public void updateEMeterNotifyInfoBatch(Set<ElectronicMeterInfo> meterInfos) {
    List<Query> queries = meterInfos.stream()
        .map(dto -> dslContext.update(Tables.ELECTRONIC_METER_INFO)
            .set(Tables.ELECTRONIC_METER_INFO.PERIODCONSUMEAMOUNT, dto.getPeriodconsumeamount())
            .set(Tables.ELECTRONIC_METER_INFO.PERIODCONSUMESTARTTIME,
                dto.getPeriodconsumestarttime())
            .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERID, dto.getUpdateuserid())
            .set(Tables.ELECTRONIC_METER_INFO.UPDATEUSERNAME, dto.getUpdateusername())
            .set(Tables.ELECTRONIC_METER_INFO.UPDATETIME, dto.getUpdatetime())
            .where(Tables.ELECTRONIC_METER_INFO.ID.eq(dto.getId())))
        .collect(Collectors.toList());

    dslContext.batch(queries).execute();
  }

  public void updateMeterMiddleState(int middleStatusIndex, String userId, String userName,
      ElectronicMeterInfo meterInfo) {
    meterInfo.setSwitchstatus(middleStatusIndex);
    meterInfo.setUpdateuserid(userId);
    meterInfo.setUpdateusername(userName);
    meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    electronicMeterInfoDao.update(meterInfo);
  }

  public ElectronicMeterInfo findEMeterInfoById(long id) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.ID.eq(id))
        .fetchOneInto(ElectronicMeterInfo.class);
  }

  public List<ElectronicMeterInfo> findEMeterInfoByIds(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.ID.in(ids))
        .fetchInto(ElectronicMeterInfo.class);
  }

  public Map<Long, ElectronicMeterInfo> findEMeterInfoByIdsMap(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.ID.in(ids))
        .fetchMap(Tables.ELECTRONIC_METER_INFO.ID, ElectronicMeterInfo.class);
  }

  public Map<Long, ElectronicMeterInfo> findEMeterInfoByIdsMapForUpdate(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.ID.in(ids))
        .forUpdate()
        .fetchMap(Tables.ELECTRONIC_METER_INFO.ID, ElectronicMeterInfo.class);
  }

  public List<ElectronicMeterInfo> findEMeterInfoByRoomIds(Collection<String> roomIds) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.BOUNDROOMID.in(roomIds))
        .fetchInto(ElectronicMeterInfo.class);
  }

  public List<ElectronicMeterInfo> findEMeterInfoByRoomId(String roomId) {
    return dslContext.selectFrom(Tables.ELECTRONIC_METER_INFO)
        .where(Tables.ELECTRONIC_METER_INFO.BOUNDROOMID.eq(roomId))
        .fetchInto(ElectronicMeterInfo.class);
  }

  public void update(ElectronicMeterInfo electronicMeterInfo) {
    electronicMeterInfoDao.update(electronicMeterInfo);
  }

  public ElectronicMeterInfo findById(long id) {
    return electronicMeterInfoDao.findById(id);
  }

  public void insert(ElectronicMeterInfo meterInfo) {
    electronicMeterInfoDao.insert(meterInfo);
  }
}
