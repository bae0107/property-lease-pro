package com.jugu.propertylease.main.propertymgr.area.entity;

import com.jugu.propertylease.main.jooq.tables.pojos.RegionInfo;
import com.jugu.propertylease.main.propertymgr.UserOpInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "区域信息结构")
public class RegionInfoDTO extends UserOpInfo {

  private RegionBaseInfo regionBaseInfo;

  public static RegionInfoDTO toDto(RegionInfo info) {
    RegionInfoDTO entity = new RegionInfoDTO();
    RegionBaseInfo baseInfo = new RegionBaseInfo();
    baseInfo.setRegionId(info.getRegionid());
    baseInfo.setProvince(info.getProvince());
    baseInfo.setCity(info.getCity());
    baseInfo.setDistrict(info.getDistrict());
    baseInfo.setRegionDes(info.getRegiondes());
    entity.setRegionBaseInfo(baseInfo);
    entity.setCreateUserId(info.getCreateuserid())
        .setCreateUserName(info.getCreateusername())
        .setCreateTime(info.getCreatetime())
        .setUpdateUserId(info.getUpdateuserid())
        .setUpdateUserName(info.getUpdateusername())
        .setUpdateTime(info.getUpdatetime())
        .setIsDeleted(info.getIsdeleted())
        .setDeleteUserId(info.getDeleteuserid())
        .setDeleteUserName(info.getDeleteusername())
        .setDeleteTime(info.getDeletetime());
    return entity;
  }
}
