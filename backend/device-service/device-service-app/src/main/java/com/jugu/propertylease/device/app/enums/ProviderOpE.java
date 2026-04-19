package com.jugu.propertylease.device.app.enums;

import com.jugu.propertylease.device.app.locker.LockRequestOp;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicRequestOp;
import com.jugu.propertylease.device.app.meter.electricity.NewElectronicMeterProcessor;
import com.jugu.propertylease.device.app.meter.water.WaterRequestOp;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum ProviderOpE {
  YUN_DING(1, "云丁", ElectronicRequestOp.YUN_DING, NewElectronicMeterProcessor.YUN_DING,
      WaterRequestOp.YUN_DING, LockRequestOp.YUN_DING),
  HE_YI(2, "合一", ElectronicRequestOp.HE_YI, NewElectronicMeterProcessor.HE_YI,
      WaterRequestOp.HE_YI, LockRequestOp.HE_YI);

  private final int index;

  private final String name;

  private final ElectronicRequestOp electronicRequestOp;

  private final WaterRequestOp waterRequestOp;

  private final NewElectronicMeterProcessor newElectronicMeterProcessor;

  private final LockRequestOp lockRequestOp;

  ProviderOpE(int index, String name, ElectronicRequestOp electronicRequestOp,
      NewElectronicMeterProcessor newElectronicMeterProcessor,
      WaterRequestOp waterRequestOp, LockRequestOp lockRequestOp) {
    this.index = index;
    this.name = name;
    this.electronicRequestOp = electronicRequestOp;
    this.newElectronicMeterProcessor = newElectronicMeterProcessor;
    this.waterRequestOp = waterRequestOp;
    this.lockRequestOp = lockRequestOp;
  }

  public static Optional<ProviderOpE> findProviderByIndex(int index) {
    for (ProviderOpE opE : ProviderOpE.values()) {
      if (opE.getIndex() == index) {
        return Optional.of(opE);
      }
    }
    return Optional.empty();
  }
}
