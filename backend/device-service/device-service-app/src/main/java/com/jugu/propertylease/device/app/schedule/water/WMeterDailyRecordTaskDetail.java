package com.jugu.propertylease.device.app.schedule.water;

import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class WMeterDailyRecordTaskDetail {

  private Set<Long> successIds;

  private Map<Long, Long> pendingTempIds;

  private Map<Long, String> failInfo;
}
