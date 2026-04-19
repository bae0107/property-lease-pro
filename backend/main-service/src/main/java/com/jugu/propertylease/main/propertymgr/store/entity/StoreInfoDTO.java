package com.jugu.propertylease.main.propertymgr.store.entity;

import com.jugu.propertylease.main.jooq.tables.pojos.StoreInfo;
import com.jugu.propertylease.main.propertymgr.UserOpInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionBaseInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "门店信息")
public class StoreInfoDTO extends UserOpInfo {

  @Schema(description = "门店基本信息")
  private StoreBaseInfo baseInfo;

  @Schema(description = "门店策略信息")
  private StoreStrategyInfo storeStrategyInfo;

  @Schema(description = "绑定的区域信息")
  private RegionBaseInfo regionBaseInfo;

  public static StoreInfoDTO toDto(StoreInfo storeInfo) {
    StoreInfoDTO dto = new StoreInfoDTO();
    StoreBaseInfo baseInfo = new StoreBaseInfo();
    baseInfo.setStoreId(storeInfo.getStoreid());
    baseInfo.setRegionId(storeInfo.getRegionid());
    baseInfo.setStoreName(storeInfo.getStorename());
    baseInfo.setStoreAddress(storeInfo.getStoreaddress());
    baseInfo.setStorePhone(storeInfo.getStorephone());
    baseInfo.setStoreDes(storeInfo.getStoredes());
    baseInfo.setMechtNo(storeInfo.getMechtno());
    baseInfo.setLinkedStore(storeInfo.getLinkedstore());
    dto.setBaseInfo(baseInfo);
    StoreStrategyInfo strategyInfo = new StoreStrategyInfo();
    strategyInfo.setEleUnitPrice(storeInfo.getEleunitprice());
    strategyInfo.setWaterUnitPrice(storeInfo.getWaterunitprice());
    strategyInfo.setWaterOverdraft(storeInfo.getWateroverdraft());
    strategyInfo.setEleOverdraft(storeInfo.getEleoverdraft());
    strategyInfo.setWaterRemind(storeInfo.getWaterremind());
    strategyInfo.setEleRemind(storeInfo.getEleremind());
    strategyInfo.setRechargeMin(storeInfo.getRechargemin());
    strategyInfo.setCanCheckinPre(storeInfo.getCancheckinpre());
    strategyInfo.setRenewTime(storeInfo.getRenewtime());
    strategyInfo.setRentFeeDay(storeInfo.getRentfeeday());
    strategyInfo.setServiceFeeDay(storeInfo.getServicefeeday());
    strategyInfo.setRefundFeeDay(storeInfo.getRefundfeeday());
    strategyInfo.setPrepayEle(storeInfo.getPrepayele());
    strategyInfo.setPrepayWater(storeInfo.getPrepaywater());
    strategyInfo.setDeposit(storeInfo.getDeposit());
    dto.setStoreStrategyInfo(strategyInfo);
    dto.setCreateUserId(storeInfo.getCreateuserid())
        .setCreateUserName(storeInfo.getCreateusername())
        .setCreateTime(storeInfo.getCreatetime())
        .setUpdateUserId(storeInfo.getUpdateuserid())
        .setUpdateUserName(storeInfo.getUpdateusername())
        .setUpdateTime(storeInfo.getUpdatetime())
        .setIsDeleted(storeInfo.getIsdeleted())
        .setDeleteUserId(storeInfo.getDeleteuserid())
        .setDeleteUserName(storeInfo.getDeleteusername())
        .setDeleteTime(storeInfo.getDeletetime());
    return dto;
  }
}
