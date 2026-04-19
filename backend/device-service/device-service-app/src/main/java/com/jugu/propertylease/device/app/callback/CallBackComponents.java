package com.jugu.propertylease.device.app.callback;

import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Getter
public class CallBackComponents {

  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  private final ElectronicMeterService electronicMeterService;

  private final WaterMeterService waterMeterService;
}
