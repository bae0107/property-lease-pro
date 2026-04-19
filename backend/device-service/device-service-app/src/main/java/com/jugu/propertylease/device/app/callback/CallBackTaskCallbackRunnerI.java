package com.jugu.propertylease.device.app.callback;

import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;

public interface CallBackTaskCallbackRunnerI {

  void callbackMeterSwitch(int recordOpType, long id,
      ElectronicMeterService electronicMeterService);

  void callbackEMeterRead(int recordOpType, long id, ElectronicMeterService electronicMeterService);

  void callbackWMeterRead(long id, WaterMeterService waterMeterService);

  void callBackLockPwdOp(ThirdPartyServiceTemp serviceTemp,
      ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr);
}
