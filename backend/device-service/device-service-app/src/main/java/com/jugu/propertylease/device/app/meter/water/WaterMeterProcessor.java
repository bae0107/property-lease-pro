package com.jugu.propertylease.device.app.meter.water;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.enums.ServiceTypeE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.jooq.tables.pojos.WaterMeterInfo;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.repository.WaterMeterRepository;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Log4j2
@Getter
public class WaterMeterProcessor {

  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  private final WaterMeterRepository waterMeterRepository;

  public ReturnDataInfo<ServiceRecordDTO> processWaterRecordAsync(WaterRecordTaskRunner taskRunner,
      String deviceId, int op) {
    DeviceResponse response = taskRunner.submitAsyncRecord(deviceId);
    if (response.isSuccess()) {
      long serviceKey = thirdPartyServiceRecordMgr.recordNewService(response.getServiceId(),
          deviceId, ThirdPartyServiceRecordMgr.DeviceType.WATER,
          ServiceTypeE.RECORD.getIndex(), response.getServiceNote(), op, "");
      return ReturnDataInfo.successData(new ServiceRecordDTO(serviceKey, op, deviceId, true));
    }
    return ReturnDataInfo.failData(response.getErrorInfo());
  }

  @Transactional
  public ReturnDataInfo<ServiceRecordDTO> processWaterRecordSync(WaterRecordTaskRunner taskRunner,
      long id, String deviceId,
      String userName, String userId, int op) {
    WMeterCheckResponse checkResponse = taskRunner.runRecordTask(deviceId, id);
    if (checkResponse.isSuccess()) {
      WaterMeterInfo waterMeterInfo = waterMeterRepository.findWMeterInfoForUpdate(id);
      if (waterMeterInfo != null) {
        setWMeterConsumeInfo(waterMeterInfo, checkResponse.getConsumeAmount(),
            checkResponse.getRecordTime(), userName, userId);
        waterMeterRepository.update(waterMeterInfo);
        return ReturnDataInfo.successData(new ServiceRecordDTO(0, op, deviceId, false));
      }
      return ReturnDataInfo.failDataByType(ErrorType.SERIOUS_ERROR, "水表数据缺失:" + id);
    }
    return ReturnDataInfo.failData(checkResponse.getErrorInfo());
  }

  @Transactional
  public boolean syncWMeterRecordTask(String serviceId, int providerOp, long id, double amount,
      String recordTime, String userName, String userId) {
    ThirdPartyServiceTemp serviceTemp = thirdPartyServiceRecordMgr.findRecordForUpdate(serviceId,
        providerOp);
    WaterMeterInfo waterMeterInfo = waterMeterRepository.findWMeterInfoForUpdate(id);
    if (serviceTemp != null && waterMeterInfo != null) {
      thirdPartyServiceRecordMgr.parseServiceResult(serviceTemp,
          ThirdPartyServiceRecordMgr.ServiceResult.SUCCESS, "");
      setWMeterConsumeInfo(waterMeterInfo, amount, recordTime, userName, userId);
      waterMeterRepository.update(waterMeterInfo);
      return true;
    }
    return false;
  }

  public boolean updateMeterConsumeInfo(WaterMeterInfo meterInfo, double consume, String recordTime,
      String userName, String userId) {
    long id = meterInfo.getId();
    double curConsume = meterInfo.getConsumeamount();
    String curRecordTime = meterInfo.getConsumerecordtime();
    if (recordTime.compareTo(curRecordTime) < 0 || Double.compare(consume, curConsume) < 0) {
      log.warn(
          "fail to update w meter due to abnormal param curConsume: {}, read: {}, curT: {}, read: {}, id: {}",
          curConsume, consume, curRecordTime, recordTime, id);
      return false;
    }
    setWMeterConsumeInfo(meterInfo, consume, recordTime, userName, userId);
    waterMeterRepository.update(meterInfo);
    return true;
  }

  private void setWMeterConsumeInfo(WaterMeterInfo meterInfo, double consume, String recordTime,
      String userName, String userId) {
    meterInfo.setConsumeamount(consume);
    meterInfo.setConsumerecordtime(recordTime);
    meterInfo.setUpdateusername(userName);
    meterInfo.setUpdateuserid(userId);
    meterInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
  }

  public interface WaterRecordTaskRunner {

    default DeviceResponse submitAsyncRecord(String deviceId) {
      DeviceResponse response = new DeviceResponse();
      response.failResponse(new ErrorInfo(-1, "not available"));
      return response;
    }

    default WMeterCheckResponse runRecordTask(String deviceId, long id) {
      WMeterCheckResponse response = new WMeterCheckResponse();
      response.failResponse(new ErrorInfo(-1, "not available"));
      return response;
    }
  }
}
