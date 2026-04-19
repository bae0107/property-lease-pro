package com.jugu.propertylease.main.propertymgr.building;

import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import java.util.Set;

// toDo: 增加filter查询
public interface BuildingServiceI {

  ReturnInfo createNewBuilding(BuildingBaseInfo baseInfo, String userName, String userId);

  ReturnInfo updateFloorsInfo(Set<Integer> floors, String buildingId, String userName,
      String userId);

  ReturnInfo deleteBuilding(String buildingId, String userName, String userId);

  ReturnDataInfo<BuildingDTO> findBuildingById(String buildingId);
}
