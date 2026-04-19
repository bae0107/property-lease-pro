package com.jugu.propertylease.main.propertymgr.store.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "门店过滤器")
public class StoreFilter {

  @Schema(description = "门店名称过滤")
  private String nameF;
}
