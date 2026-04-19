package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.DeviceEventInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.DeviceEventInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class EventInfoRepository {

  private final DSLContext dslContext;

  private final DeviceEventInfoDao deviceEventInfoDao;

  public void addNewEventInfo(long deviceKey, int providerOp, int deviceType, String eventName,
      String eventInfo) {
    DeviceEventInfo deviceEventInfo = new DeviceEventInfo();
    deviceEventInfo.setDevicekey(deviceKey)
        .setProviderop(providerOp)
        .setDevicetype(deviceType)
        .setEventname(eventName)
        .setEventinfo(eventInfo)
        .setEventtime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    deviceEventInfoDao.insert(deviceEventInfo);
  }

  public List<DeviceEventInfo> findEventInfosBatchByCondition(int batchSize, int passNum,
      int hasRead) {
    return dslContext.selectFrom(Tables.DEVICE_EVENT_INFO)
        .where(Tables.DEVICE_EVENT_INFO.HASREAD.eq(hasRead))
        .orderBy(Tables.DEVICE_EVENT_INFO.EVENTTIME.desc())
        .limit(batchSize)
        .offset(passNum)
        .fetchInto(DeviceEventInfo.class);
  }

  public int countSizeByCondition(int hasRead) {
    return dslContext.fetchCount(Tables.DEVICE_EVENT_INFO,
        Tables.DEVICE_EVENT_INFO.HASREAD.eq(hasRead));
  }

  public void readEvent(long id) {
    dslContext.update(Tables.DEVICE_EVENT_INFO)
        .set(Tables.DEVICE_EVENT_INFO.HASREAD, 2)
        .where(Tables.DEVICE_EVENT_INFO.ID.eq(id))
        .execute();
  }
}
