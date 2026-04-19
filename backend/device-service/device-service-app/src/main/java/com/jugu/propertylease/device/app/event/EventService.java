package com.jugu.propertylease.device.app.event;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.tables.pojos.DeviceEventInfo;
import com.jugu.propertylease.device.app.repository.EventInfoRepository;
import com.jugu.propertylease.device.common.entity.dto.EventInfoDTO;
import com.jugu.propertylease.device.common.entity.request.EventInfoRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
public class EventService {

  private final EventInfoRepository eventInfoRepository;

  public ReturnDataInfo<List<EventInfoDTO>> findBatchEventInfo(EventInfoRequest eventInfoRequest) {
    try {
      List<DeviceEventInfo> infos = eventInfoRepository.findEventInfosBatchByCondition(
          eventInfoRequest.getBatchSize(),
          eventInfoRequest.getPassNum(), eventInfoRequest.getHasRead());
      return Common.isCollectionInValid(infos)
          ? ReturnDataInfo.successData(new ArrayList<>())
          : ReturnDataInfo.successData(
              infos.stream().map(this::toDTO).collect(Collectors.toList()));
    } catch (Exception e) {
      log.error("fail to find event info due to db error");
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  public ReturnDataInfo<Integer> countSizeByCondition(EventInfoRequest eventInfoRequest) {
    try {
      return ReturnDataInfo.successData(
          eventInfoRepository.countSizeByCondition(eventInfoRequest.getHasRead()));
    } catch (Exception e) {
      log.error("fail to count event info size due to db error");
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  public ReturnInfo readEventInfoMsg(long id) {
    try {
      eventInfoRepository.readEvent(id);
      return ReturnInfo.success();
    } catch (Exception e) {
      log.error("fail read event info due to db error");
      return ReturnInfo.failByType(ErrorType.DB_ERROR);
    }
  }

  private EventInfoDTO toDTO(DeviceEventInfo deviceEventInfo) {
    EventInfoDTO infoDTO = new EventInfoDTO();
    infoDTO.setId(deviceEventInfo.getId())
        .setDeviceKey(deviceEventInfo.getDevicekey())
        .setProviderOp(deviceEventInfo.getProviderop())
        .setDeviceType(deviceEventInfo.getDevicetype())
        .setEventName(deviceEventInfo.getEventname())
        .setEventInfo(deviceEventInfo.getEventinfo())
        .setHasRead(deviceEventInfo.getHasread())
        .setEventTime(deviceEventInfo.getEventtime());
    return infoDTO;
  }
}
