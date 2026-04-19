package com.jugu.propertylease.main.propertymgr.area.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "区域过滤字段")
public class RegionFilter {

  @Schema(description = "区域简介过滤")
  private String desF;

  @Schema(description = "区域编号过滤")
  private String idF;
}
