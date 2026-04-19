package com.jugu.propertylease.device.app.meter.water;

import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;

public interface WMeterI {

  WMeterCheckResponse findWMeterInfoRequest(String deviceId);

  DeviceResponse sendWaterRecordRequest(String deviceId);

  ReturnDataInfo<ServiceRecordDTO> readWaterRecordRequest(String deviceId,
      WaterMeterProcessor meterProcessor, long id, String userName, String userId);
}
