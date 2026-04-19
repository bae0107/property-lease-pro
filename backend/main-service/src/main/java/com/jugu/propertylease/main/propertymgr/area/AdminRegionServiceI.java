package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionBaseInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionFilter;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionInfoDTO;
import java.util.List;
import java.util.Set;

public interface AdminRegionServiceI {

  /**
   * 获取中国所有区域列表信息，省，市，区（直辖市提供）
   *
   * @return 区域信息列表
   */
  ReturnDataInfo<List<AdminRegions.Province>> findAllRegionSettings();

  ReturnInfo addNewRegion(RequestDataInfo<CreateRegionRequest> createRegionRequest);

  ReturnDataInfo<RegionInfoDTO> findRegionById(String regionId);

  ReturnDataInfo<List<RegionInfoDTO>> findRegionsByIds(Set<String> regionIds);

  ReturnInfo updateRegionDes(RegionBaseInfo baseInfo, String userName, String userId);

  ReturnInfo deleteRegion(String regionId, String userName, String userId);

  ReturnDataInfo<List<RegionInfoDTO>> findRegionsByFilter(RegionFilter regionFilter);
}
