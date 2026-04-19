package com.jugu.propertylease.main.propertymgr.building;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "楼栋基础信息")
public class BuildingBaseInfo {

  @Schema(description = "楼栋id（自生成）")
  private String buildingId;

  @Schema(description = "绑定的门店ID")
  private long storeId;

  @Schema(description = "楼栋名称")
  private String buildingName;
}
