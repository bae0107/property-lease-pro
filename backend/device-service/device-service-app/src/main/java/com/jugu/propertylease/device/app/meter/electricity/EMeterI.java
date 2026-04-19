package com.jugu.propertylease.device.app.meter.electricity;

import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;

/**
 * 用于第三方表类接口调用的功能抽象
 */
public interface EMeterI {

  EMeterSwitchResponse switchMeterRequest(String deviceId,
      EMeterSwitchResponse.SwitchTypeE switchTypeE);

  EMeterCheckResponse readMeterRequest(String deviceId);

  EMeterPeriodResponse findUsagePeriod(String deviceId, long startTime, long endTime);

  ReturnDataInfo<Integer> adjustSwitch(EMeterSwitchResponse.SwitchTypeE switchTypeE,
      ElectronicSwitchProcessor switchProcessor,
      String userName, String userId, ElectronicMeterInfo meterInfo);

  EMeterCheckResponse checkMeterFullState(String deviceId);
}
