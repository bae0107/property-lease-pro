package com.jugu.propertylease.main.propertymgr.store.entity;

import com.jugu.propertylease.main.jooq.tables.pojos.StoreInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Schema(description = "门店基本信息")
public class StoreBaseInfo {

  @Schema(description = "门店ID（业务内部）")
  private long storeId;

  @Schema(description = "省/直辖市名称")
  private String regionId;

  @Schema(description = "门店名称")
  private String storeName;

  @Schema(description = "门店地址")
  private String storeAddress;

  @Schema(description = "门店电话")
  private String storePhone;

  @Schema(description = "门店描述")
  private String storeDes;

  @Schema(description = "商户号")
  private String mechtNo;

  @Schema(description = "关联门店号")
  private String linkedStore;

  public void parseBaseToStore(StoreInfo storeInfo) {
    storeInfo.setRegionid(regionId)
        .setStorename(storeName)
        .setStoreaddress(storeAddress)
        .setStorephone(storePhone)
        .setStoredes(storeDes)
        .setMechtno(mechtNo)
        .setLinkedstore(linkedStore);
  }
}
