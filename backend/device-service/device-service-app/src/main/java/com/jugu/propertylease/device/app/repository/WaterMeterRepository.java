package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.WaterMeterInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.common.entity.dto.WMeterInfoDTO;
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
public class WaterMeterRepository {

  private final DSLContext dslContext;

  private final WaterMeterInfoDao waterMeterInfoDao;

  public void update(WaterMeterInfo waterMeterInfo) {
    waterMeterInfoDao.update(waterMeterInfo);
  }

  public List<WaterMeterInfo> findWMeterInfoByRoomIds(Collection<String> roomIds) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.BOUNDROOMID.in(roomIds))
        .fetchInto(WaterMeterInfo.class);
  }

  public void updateBoundRoom(long id, String roomId, String userName, String userId, String time) {
    dslContext.update(Tables.WATER_METER_INFO)
        .set(Tables.WATER_METER_INFO.BOUNDROOMID, roomId)
        .set(Tables.WATER_METER_INFO.UPDATEUSERID, userId)
        .set(Tables.WATER_METER_INFO.UPDATEUSERNAME, userName)
        .set(Tables.WATER_METER_INFO.UPDATETIME, time)
        .where(Tables.WATER_METER_INFO.ID.eq(id))
        .execute();
  }

  public int updateOnlineStatus(int status, String time, String userName, String userId, int op,
      String deviceId) {
    return dslContext.update(Tables.WATER_METER_INFO)
        .set(Tables.WATER_METER_INFO.STATUS, status)
        .set(Tables.WATER_METER_INFO.STATUSTIME, time)
        .set(Tables.WATER_METER_INFO.UPDATEUSERID, userId)
        .set(Tables.WATER_METER_INFO.UPDATEUSERNAME, userName)
        .set(Tables.WATER_METER_INFO.UPDATETIME, time)
        .where(Tables.WATER_METER_INFO.PROVIDEROP.eq(op))
        .and(Tables.WATER_METER_INFO.DEVICEID.eq(deviceId))
        .execute();
  }

  public void updateGateWayStatus(int status, String time, String userName, String userId, int op,
      String deviceId) {
    dslContext.update(Tables.WATER_METER_INFO)
        .set(Tables.WATER_METER_INFO.GATEWAYSTATUS, status)
        .set(Tables.WATER_METER_INFO.UPDATEUSERID, userId)
        .set(Tables.WATER_METER_INFO.UPDATEUSERNAME, userName)
        .set(Tables.WATER_METER_INFO.UPDATETIME, time)
        .where(Tables.WATER_METER_INFO.PROVIDEROP.eq(op))
        .and(Tables.WATER_METER_INFO.DEVICEID.eq(deviceId))
        .execute();
  }

  public List<WaterMeterInfo> findWMeterInfoByRoomId(String roomId) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.BOUNDROOMID.eq(roomId))
        .fetchInto(WaterMeterInfo.class);
  }

  public List<WaterMeterInfo> findWMeterInfoByIds(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ID.in(ids))
        .fetchInto(WaterMeterInfo.class);
  }

  public List<WaterMeterInfo> findWMeterInfoByDeviceIds(Collection<String> deviceIds) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.DEVICEID.in(deviceIds))
        .fetchInto(WaterMeterInfo.class);
  }

  public Map<Long, WaterMeterInfo> findWMeterInfoByIdsMap(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ID.in(ids))
        .fetchMap(Tables.WATER_METER_INFO.ID, WaterMeterInfo.class);
  }

  public Map<Long, WaterMeterInfo> findWMeterInfoByIdsMapForUpdate(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ID.in(ids))
        .forUpdate()
        .fetchMap(Tables.WATER_METER_INFO.ID, WaterMeterInfo.class);
  }

  public void updateWMeterNotifyInfoBatch(Set<WaterMeterInfo> meterInfos) {
    List<Query> queries = meterInfos.stream()
        .map(dto -> dslContext.update(Tables.WATER_METER_INFO)
            .set(Tables.WATER_METER_INFO.PERIODCONSUMEAMOUNT, dto.getPeriodconsumeamount())
            .set(Tables.WATER_METER_INFO.PERIODCONSUMESTARTTIME, dto.getPeriodconsumestarttime())
            .set(Tables.WATER_METER_INFO.UPDATEUSERID, dto.getUpdateuserid())
            .set(Tables.WATER_METER_INFO.UPDATEUSERNAME, dto.getUpdateusername())
            .set(Tables.WATER_METER_INFO.UPDATETIME, dto.getUpdatetime())
            .where(Tables.WATER_METER_INFO.ID.eq(dto.getId())))
        .collect(Collectors.toList());

    dslContext.batch(queries).execute();
  }

  public List<WaterMeterInfo> findAvailableWMeterInfos() {
    return dslContext.select(
            Tables.WATER_METER_INFO.ID,
            Tables.WATER_METER_INFO.CONSUMERECORDTIME,
            Tables.WATER_METER_INFO.PROVIDEROP,
            Tables.WATER_METER_INFO.DEVICEID)
        .from(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ISDELETED.eq(1))
        .fetchInto(WaterMeterInfo.class);
  }

  public WaterMeterInfo findWMeterInfoByDeviceIdAndProvider(String deviceId, int provider) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.DEVICEID.eq(deviceId))
        .and(Tables.WATER_METER_INFO.PROVIDEROP.eq(provider))
        .fetchOneInto(WaterMeterInfo.class);
  }

  public WaterMeterInfo findWMeterInfoByDeviceIdAndProviderForUpdate(String deviceId,
      int provider) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.DEVICEID.eq(deviceId))
        .and(Tables.WATER_METER_INFO.PROVIDEROP.eq(provider))
        .forUpdate()
        .fetchOneInto(WaterMeterInfo.class);
  }

  public WaterMeterInfo findWMeterInfoById(long id) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ID.eq(id))
        .fetchOneInto(WaterMeterInfo.class);
  }

  public WaterMeterInfo findWMeterInfoForUpdate(long id) {
    return dslContext.selectFrom(Tables.WATER_METER_INFO)
        .where(Tables.WATER_METER_INFO.ID.eq(id))
        .forUpdate()
        .fetchOneInto(WaterMeterInfo.class);
  }

  public void addNewWaterMeter(WMeterInfoDTO infoDTO) {
    WaterMeterInfo meterInfo = new WaterMeterInfo();
    meterInfo.setMetertype(infoDTO.getMeterType())
        .setGatewaystatus(infoDTO.getGateWayStatus())
        .setInstalltype(infoDTO.getInstallType())
        .setMeterno(infoDTO.getMeterNo())
        .setBoundroomid(infoDTO.getBoundRoomId())
        .setBoundtimechange(infoDTO.getBoundTimeChange())
        .setConsumeamount(infoDTO.getConsumeAmount())
        .setConsumerecordtime(infoDTO.getConsumeRecordTime())
        .setPeriodconsumestarttime(infoDTO.getPeriodConsumeStartTime())
        .setPeriodconsumeamount(infoDTO.getPeriodConsumeAmount())
        .setStatus(infoDTO.getStatus())
        .setStatustime(infoDTO.getStatusTime())
        .setDeviceid(infoDTO.getDeviceId())
        .setDevicemodelid(infoDTO.getDeviceModelId())
        // 注意别的供应商的meterNo是否为采集器ID
        .setCollectorid(infoDTO.getMeterNo())
        .setProvidername(infoDTO.getProviderName())
        .setProviderop(infoDTO.getProviderOp())
        .setCollectorid(infoDTO.getCollectorId())
        .setCreateuserid(infoDTO.getCreateUserId())
        .setCreateusername(infoDTO.getCreateUserName())
        .setCreatetime(infoDTO.getCreateTime());
    waterMeterInfoDao.insert(meterInfo);
  }
}
