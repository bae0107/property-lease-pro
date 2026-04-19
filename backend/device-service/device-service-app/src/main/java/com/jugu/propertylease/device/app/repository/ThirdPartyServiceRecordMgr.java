package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.ThirdPartyServiceTempDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
public class ThirdPartyServiceRecordMgr {

  private final ThirdPartyServiceTempDao thirdPartyServiceTempDao;

  private final DSLContext dslContext;

  public long recordNewService(String serviceId, String deviceId, DeviceType deviceType,
      int serviceType, String serviceNote, int providerOp, String addInfo) {
    ThirdPartyServiceTemp serviceTemp = new ThirdPartyServiceTemp();
    setNewRecord(serviceTemp, serviceId, deviceId, deviceType, serviceType, serviceNote, providerOp,
        addInfo);
    thirdPartyServiceTempDao.insert(serviceTemp);
    return serviceTemp.getId();
  }

  public long recordNewServiceWithKey(String serviceId, String deviceId, DeviceType deviceType,
      int serviceType, String serviceNote, int providerOp, String addInfo) {
    ThirdPartyServiceTemp serviceTemp = new ThirdPartyServiceTemp();
    setNewRecord(serviceTemp, serviceId, deviceId, deviceType, serviceType, serviceNote, providerOp,
        addInfo);
    thirdPartyServiceTempDao.insert(serviceTemp);
    return serviceTemp.getId();
  }

  public long insertRecord(ThirdPartyServiceTemp serviceTemp) {
    thirdPartyServiceTempDao.insert(serviceTemp);
    return serviceTemp.getId();
  }

  public ThirdPartyServiceTemp findRecordForUpdate(String serviceId, int providerOp) {
    return dslContext.selectFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.SERVICEID.eq(serviceId))
        .and(Tables.THIRD_PARTY_SERVICE_TEMP.PROVIDEROP.eq(providerOp))
        .forUpdate()
        .fetchOneInto(ThirdPartyServiceTemp.class);
  }

  public ThirdPartyServiceTemp findRecord(String serviceId, int providerOp) {
    return dslContext.selectFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.SERVICEID.eq(serviceId))
        .and(Tables.THIRD_PARTY_SERVICE_TEMP.PROVIDEROP.eq(providerOp))
        .fetchOneInto(ThirdPartyServiceTemp.class);
  }

  public Optional<Integer> checkResult(long serviceKey) {
    return dslContext.select(Tables.THIRD_PARTY_SERVICE_TEMP.SERVICERESULT)
        .from(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.ID.eq(serviceKey))
        .fetchOptional(Tables.THIRD_PARTY_SERVICE_TEMP.SERVICERESULT);
  }

  public List<ThirdPartyServiceTemp> findIdsByAddInfoAndOp(String taskId, int op) {
    return dslContext.selectFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.ADDITIONALINFO.eq(taskId))
        .and(Tables.THIRD_PARTY_SERVICE_TEMP.PROVIDEROP.eq(op))
        .fetchInto(ThirdPartyServiceTemp.class);
  }

  public Map<Long, ThirdPartyServiceTemp> findServiceTempsByIds(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.ID.in(ids))
        .fetchMap(Tables.THIRD_PARTY_SERVICE_TEMP.ID, ThirdPartyServiceTemp.class);
  }

  public void deleteServiceByAddInfo(String addInfo) {
    dslContext.deleteFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.ADDITIONALINFO.eq(addInfo))
        .execute();
  }

  public void deleteServiceByAddIds(Collection<Long> ids) {
    dslContext.deleteFrom(Tables.THIRD_PARTY_SERVICE_TEMP)
        .where(Tables.THIRD_PARTY_SERVICE_TEMP.ID.in(ids))
        .execute();
  }

  public void parseServiceResult(ThirdPartyServiceTemp serviceTemp, ServiceResult result,
      String errorInfo) {
    serviceTemp.setServiceedtime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
    serviceTemp.setServiceresult(result.index);
    if (result == ServiceResult.FAIL) {
      serviceTemp.setServiceerror(errorInfo);
    }
    thirdPartyServiceTempDao.update(serviceTemp);
  }

  private void setNewRecord(ThirdPartyServiceTemp serviceTemp, String serviceId, String deviceId,
      DeviceType deviceType,
      int serviceType, String serviceNote, int providerOp, String addInfo) {
    String stTime = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
    serviceTemp.setServiceid(serviceId);
    serviceTemp.setDeviceid(deviceId);
    serviceTemp.setProviderop(providerOp);
    serviceTemp.setDevicetype(deviceType.getIndex());
    serviceTemp.setServicetype(serviceType);
    serviceTemp.setServicenote(serviceNote);
    serviceTemp.setServicesttime(stTime);
    serviceTemp.setServiceresult(ServiceResult.IN_PROGRESSING.getIndex());
    serviceTemp.setAdditionalinfo(addInfo);
  }

  @Getter
  public enum DeviceType {
    ELECTRICITY(1),
    WATER(2),
    LOCKER(3);

    private final int index;

    DeviceType(int index) {
      this.index = index;
    }

    public static Optional<DeviceType> findTypeByIndex(int index) {
      for (DeviceType deviceType : DeviceType.values()) {
        if (deviceType.getIndex() == index) {
          return Optional.of(deviceType);
        }
      }
      return Optional.empty();
    }
  }

  @Getter
  public enum ServiceResult {
    IN_PROGRESSING(0),
    SUCCESS(1),
    FAIL(2);

    private final int index;

    ServiceResult(int index) {
      this.index = index;
    }
  }
}
