package com.jugu.propertylease.main.propertymgr.store.entity;

import com.jugu.propertylease.main.jooq.tables.pojos.StoreInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.ObjectUtils;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Schema(description = "门店策略信息")
public class StoreStrategyInfo {

  @Schema(description = "电费单位价格")
  private Double eleUnitPrice;

  @Schema(description = "水费单位价格")
  private Double waterUnitPrice;

  @Schema(description = "水费可透支额度")
  private Integer waterOverdraft;

  @Schema(description = "电费可透支额度")
  private Integer eleOverdraft;

  @Schema(description = "水费预警金额")
  private Integer waterRemind;

  @Schema(description = "电费预警金额")
  private Integer eleRemind;

  @Schema(description = "充值最小金额")
  private Integer rechargeMin;

  @Schema(description = "允许提前办理入住")
  private Byte canCheckinPre;

  @Schema(description = "0为不做设置1为到期前1个月")
  private Byte renewTime;

  @Schema(description = "提前交租天数")
  private Integer rentFeeDay;

  @Schema(description = "提前生成服务费天数")
  private Integer serviceFeeDay;

  @Schema(description = "退款天数")
  private Integer refundFeeDay;

  @Schema(description = "预缴纳电费")
  private Integer prepayEle;

  @Schema(description = "预缴纳水费")
  private Integer prepayWater;

  @Schema(description = "床位押金")
  private Integer deposit;

  public void parseStrategyToStore(StoreInfo storeInfo) {
    storeInfo.setEleunitprice(eleUnitPrice)
        .setWaterunitprice(waterUnitPrice)
        .setWateroverdraft(waterOverdraft)
        .setEleoverdraft(eleOverdraft)
        .setWaterremind(waterRemind)
        .setEleremind(eleRemind)
        .setRechargemin(rechargeMin)
        .setCancheckinpre(canCheckinPre)
        .setRenewtime(renewTime)
        .setRentfeeday(rentFeeDay)
        .setServicefeeday(serviceFeeDay)
        .setRefundfeeday(refundFeeDay)
        .setPrepayele(prepayEle)
        .setPrepaywater(prepayWater)
        .setDeposit(deposit);
  }

  public boolean checkStrategyLegal() {
    return ObjectUtils.allNotNull(
        eleUnitPrice, waterUnitPrice, waterOverdraft, eleOverdraft, waterRemind, eleRemind,
        rechargeMin, canCheckinPre, renewTime, rentFeeDay, serviceFeeDay, refundFeeDay,
        prepayEle, prepayWater, deposit
    );
  }
}
