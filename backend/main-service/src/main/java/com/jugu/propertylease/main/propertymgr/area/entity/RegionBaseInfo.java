package com.jugu.propertylease.main.propertymgr.area.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "区域基础信息")
public class RegionBaseInfo {

  @Schema(description = "区域ID")
  private String regionId;

  @Schema(description = "省级")
  private String province;

  @Schema(description = "市级")
  private String city;

  @Schema(description = "区级")
  private String district;

  @Schema(description = "区域描述")
  private String regionDes;
}
