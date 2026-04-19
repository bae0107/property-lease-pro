package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.jooq.tables.pojos.RegionInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionBaseInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionFilter;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionInfoDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class AdminRegionService implements AdminRegionServiceI {

  private final AdminRegions adminRegions;

  private final RegionRepository regionRepository;

  @Override
  public ReturnDataInfo<List<AdminRegions.Province>> findAllRegionSettings() {
    return ReturnDataInfo.successData(adminRegions.getProvinces());
  }

  @Override
  public ReturnInfo addNewRegion(RequestDataInfo<CreateRegionRequest> createRegionRequest) {
    if (!createRegionRequest.isValid()) {
      log.error("add new Region failed due to userName/id invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    CreateRegionRequest regionRequest = createRegionRequest.getData();
    if (regionRequest == null) {
      log.error("add new Region failed due to request invalid");
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
    try {
      RegionInfo regionInfo = new RegionInfo();
      adminRegions.parseRegion(createRegionRequest.getData(), regionInfo);
      regionInfo.setCreateuserid(createRegionRequest.getUserId())
          .setCreateusername(createRegionRequest.getUserName())
          .setCreatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()))
          .setRegiondes(regionRequest.getRegionDes());
      try {
        regionRepository.insert(regionInfo);
        return ReturnInfo.success();
      } catch (Exception e) {
        log.error("add new Region failed due to db error:{}", e.getMessage());
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    } catch (IllegalArgumentException e) {
      log.error("add new Region failed due to input invalid:{}", e.getMessage());
      return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
    }
  }

  @Override
  public ReturnDataInfo<RegionInfoDTO> findRegionById(String regionId) {
    if (!Common.isStringInValid(regionId)) {
      try {
        RegionInfo regionInfo = regionRepository.findRegionById(regionId);
        if (regionInfo == null) {
          return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
        }
        return ReturnDataInfo.successData(RegionInfoDTO.toDto(regionInfo));
      } catch (Exception e) {
        log.error("find region:{} failed due to db error!", regionId);
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnDataInfo<List<RegionInfoDTO>> findRegionsByIds(Set<String> regionIds) {
    if (!Common.isCollectionInValid(regionIds)) {
      try {
        List<RegionInfo> regionInfos = regionRepository.findRegionsByIds(regionIds);
        if (Common.isCollectionInValid(regionInfos)) {
          return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
        }
        return ReturnDataInfo.successData(
            regionInfos.stream().map(RegionInfoDTO::toDto).collect(Collectors.toList()));
      } catch (Exception e) {
        log.error("find regions:{} failed due to db error!", regionIds);
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnInfo updateRegionDes(RegionBaseInfo baseInfo, String userName, String userId) {
    String des = baseInfo.getRegionDes();
    String regionId = baseInfo.getRegionId();
    if (!Common.isStringInValid(des) && !Common.isStringInValid(regionId)) {
      try {
        int count = regionRepository.updateRegionDes(des, regionId, userId, userName,
            Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
        if (count != 1) {
          return ReturnInfo.failByType(ErrorType.INPUT_ERROR, "更新区域描述：" + count + "条");
        }
        return ReturnInfo.success();
      } catch (Exception e) {
        log.error("update regions:{} des failed due to db error!", regionId);
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnInfo deleteRegion(String regionId, String userName, String userId) {
    return null;
  }

  @Override
  public ReturnDataInfo<List<RegionInfoDTO>> findRegionsByFilter(RegionFilter regionFilter) {
    try {
      List<RegionInfo> regionInfos = regionRepository.findRegionsByFilter(regionFilter);
      return ReturnDataInfo.successData(Common.isCollectionInValid(regionInfos) ? new ArrayList<>()
          : regionInfos.stream().map(RegionInfoDTO::toDto).collect(Collectors.toList()));
    } catch (Exception e) {
      log.error("find all regions failed due to db error!");
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }
}
