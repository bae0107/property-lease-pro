package com.jugu.propertylease.main.propertymgr.building;

import com.jugu.propertylease.main.jooq.tables.pojos.BuildingInfo;
import com.jugu.propertylease.main.propertymgr.UserOpInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionBaseInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreBaseInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "楼栋信息")
public class BuildingDTO extends UserOpInfo {

  private BuildingBaseInfo baseInfo;

  @Schema(description = "楼层信息")
  private Set<Integer> floors;

  @Schema(description = "门店基本信息")
  private StoreBaseInfo storeBaseInfo;

  @Schema(description = "绑定的区域信息")
  private RegionBaseInfo regionBaseInfo;

  public static BuildingDTO toDto(BuildingInfo buildingInfo, StoreBaseInfo storeBaseInfo,
      RegionBaseInfo regionBaseInfo) {
    BuildingDTO buildingDTO = new BuildingDTO();
    BuildingBaseInfo buildingBaseInfo = new BuildingBaseInfo();
    buildingBaseInfo.setBuildingId(buildingInfo.getBuildingid());
    buildingBaseInfo.setBuildingName(buildingInfo.getBuildingname());
    buildingBaseInfo.setStoreId(buildingInfo.getStoreid());
    buildingDTO.setFloors(buildingInfo.getFloors());
    buildingDTO.setCreateUserId(buildingInfo.getCreateuserid())
        .setCreateUserName(buildingInfo.getCreateusername())
        .setCreateTime(buildingInfo.getCreatetime())
        .setUpdateUserId(buildingInfo.getUpdateuserid())
        .setUpdateUserName(buildingInfo.getUpdateusername())
        .setUpdateTime(buildingInfo.getUpdatetime())
        .setIsDeleted(buildingInfo.getIsdeleted())
        .setDeleteUserId(buildingInfo.getDeleteuserid())
        .setDeleteUserName(buildingInfo.getDeleteusername())
        .setDeleteTime(buildingInfo.getDeletetime());
    buildingDTO.setBaseInfo(buildingBaseInfo);
    buildingDTO.setStoreBaseInfo(storeBaseInfo);
    buildingDTO.setRegionBaseInfo(regionBaseInfo);
    return buildingDTO;
  }
}
