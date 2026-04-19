package com.jugu.propertylease.device.app.meter.electricity;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.meter.MeterInstallTypeE;
import com.jugu.propertylease.device.common.entity.dto.EMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public enum NewElectronicMeterProcessor {
  YUN_DING(new Processor() {
    @Override
    public Optional<ErrorType> parse(String userName, String userId, ProviderOpE provider,
        ElectronicMeterInfo electronicMeterInfo, EMeterInfoDTO EMeterInfoDTO) {
      electronicMeterInfo.setCreateuserid(userId);
      electronicMeterInfo.setCreateusername(userName);
      String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
      electronicMeterInfo.setCreatetime(time);
      electronicMeterInfo.setBoundtimechange(time);
      return NewElectronicMeterProcessor.checkAndParseCommonInputValid(provider,
          electronicMeterInfo, EMeterInfoDTO);
    }

    @Override
    public void process(EMeterCheckResponse EMeterCheckResponse,
        ElectronicMeterInfo electronicMeterInfo) {
      electronicMeterInfo.setCollectorid(electronicMeterInfo.getMeterno());
      parseDataFromResponse(EMeterCheckResponse, electronicMeterInfo);
    }
  }),

  HE_YI(new Processor() {
    @Override
    public Optional<ErrorType> parse(String userName, String userId, ProviderOpE provider,
        ElectronicMeterInfo electronicMeterInfo, EMeterInfoDTO EMeterInfoDTO) {
      electronicMeterInfo.setCreateuserid(userId);
      electronicMeterInfo.setCreateusername(userName);
      String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
      electronicMeterInfo.setCreatetime(time);
      electronicMeterInfo.setBoundtimechange(time);
      return NewElectronicMeterProcessor.checkAndParseCommonInputValid(provider,
          electronicMeterInfo, EMeterInfoDTO);
    }

    @Override
    public void process(EMeterCheckResponse EMeterCheckResponse,
        ElectronicMeterInfo electronicMeterInfo) {
      parseDataFromResponse(EMeterCheckResponse, electronicMeterInfo);
    }
  });

  private final Processor processor;

  NewElectronicMeterProcessor(Processor processor) {
    this.processor = processor;
  }

  private static void parseDataFromResponse(EMeterCheckResponse EMeterCheckResponse,
      ElectronicMeterInfo electronicMeterInfo) {
    double consume = EMeterCheckResponse.getConsumeAmount();
    electronicMeterInfo.setConsumeamount(consume);
    String recordTime = EMeterCheckResponse.getRecordTime();
    electronicMeterInfo.setConsumerecordtime(recordTime);
    electronicMeterInfo.setSwitchstatus(EMeterCheckResponse.getEnableState());
    electronicMeterInfo.setEnablestatetime(EMeterCheckResponse.getEnableStateTime());
    electronicMeterInfo.setStatus(EMeterCheckResponse.getOnOffLine());
    electronicMeterInfo.setStatustime(EMeterCheckResponse.getOnOffTime());
    electronicMeterInfo.setLastcommtime(EMeterCheckResponse.getTransStatusTime());
    electronicMeterInfo.setPeriodconsumestarttime(recordTime);
    electronicMeterInfo.setPeriodconsumeamount(consume);
  }

  private static Optional<ErrorType> checkAndParseCommonInputValid(ProviderOpE provider,
      ElectronicMeterInfo electronicMeterInfo, EMeterInfoDTO EMeterInfoDTO) {
    int installType = EMeterInfoDTO.getInstallType();
    if (!MeterInstallTypeE.isIndexValid(installType)) {
      log.error("fail to add new ele meter due to illegal installType:{}", installType);
      return Optional.of(ErrorType.INPUT_ERROR);
    }

    String meterNo = EMeterInfoDTO.getMeterNo();
    String boundRoomId = EMeterInfoDTO.getBoundRoomId();
    String deviceId = EMeterInfoDTO.getDeviceId();
    String modelId = EMeterInfoDTO.getDeviceModelId();
    if (Common.isStringInValid(meterNo) || Common.isStringInValid(boundRoomId)
        || Common.isStringInValid(deviceId) || Common.isStringInValid(modelId)) {
      log.error(
          "fail to add new ele meter due to illegal input-meterNo:{}, boundRoomId:{}, deviceId:{}",
          meterNo, boundRoomId, deviceId);
      return Optional.of(ErrorType.INPUT_ERROR);
    }

    electronicMeterInfo.setInstalltype(installType);
    electronicMeterInfo.setMeterno(meterNo);
    electronicMeterInfo.setBoundroomid(boundRoomId);
    electronicMeterInfo.setDeviceid(deviceId);
    electronicMeterInfo.setProvidername(provider.getName());
    electronicMeterInfo.setProviderop(provider.getIndex());
    electronicMeterInfo.setDevicemodelid(modelId);
    return Optional.empty();
  }

  interface Processor {

    Optional<ErrorType> parse(String userName, String userId, ProviderOpE provider,
        ElectronicMeterInfo electronicMeterInfo, EMeterInfoDTO EMeterInfoDTO);

    void process(EMeterCheckResponse EMeterCheckResponse, ElectronicMeterInfo electronicMeterInfo);
  }
}
