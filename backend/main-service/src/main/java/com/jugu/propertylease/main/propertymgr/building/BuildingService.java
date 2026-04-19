package com.jugu.propertylease.main.propertymgr.building;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.jooq.tables.pojos.BuildingInfo;
import com.jugu.propertylease.main.propertymgr.store.StoreService;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreInfoDTO;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class BuildingService implements BuildingServiceI {

  private static final ReentrantLock createLock = new ReentrantLock(true);

  private final StoreService storeService;

  private final BuildingRepository buildingRepository;

  @Override
  public ReturnInfo createNewBuilding(BuildingBaseInfo baseInfo, String userName, String userId) {
    String buildingName = baseInfo.getBuildingName();
    if (!Common.isStringInValid(buildingName)) {
      long storeId = baseInfo.getStoreId();
      BuildingInfo buildingInfo = new BuildingInfo();
      buildingInfo.setBuildingname(buildingName)
          .setStoreid(storeId)
          .setCreateuserid(userId)
          .setCreateusername(userName)
          .setCreatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
      createLock.lock();
      try {
        int storeCount = storeService.getStoreRepository().countById(storeId);
        if (storeCount > 0) {
          buildingInfo.setBuildingid(
              storeId + "-" + (buildingRepository.countByStore(storeId) + 1));
          buildingRepository.addNewBuilding(buildingInfo);
          return ReturnInfo.success();
        }
      } catch (Exception e) {
        log.error("create build failed due to db error:{}", e.getMessage());
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      } finally {
        createLock.unlock();
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnInfo updateFloorsInfo(Set<Integer> floors, String buildingId, String userName,
      String userId) {
    if (!Common.isStringInValid(buildingId)) {
      try {
        BuildingInfo buildingInfo = buildingRepository.findBuildingById(buildingId);
        if (buildingInfo != null) {
          buildingInfo.setFloors(floors)
              .setUpdateuserid(userId)
              .setUpdateusername(userName)
              .setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          buildingRepository.update(buildingInfo);
          return ReturnInfo.success();
        }
      } catch (Exception e) {
        log.error("fail to update build:{} due to db error", buildingId);
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnInfo deleteBuilding(String buildingId, String userName, String userId) {
    return null;
  }

  @Override
  public ReturnDataInfo<BuildingDTO> findBuildingById(String buildingId) {
    if (!Common.isStringInValid(buildingId)) {
      try {
        BuildingInfo buildingInfo = buildingRepository.findBuildingById(buildingId);
        if (buildingInfo != null) {
          ReturnDataInfo<StoreInfoDTO> storeRes = storeService.findStoreById(
              buildingInfo.getStoreid());
          if (storeRes.isSuccess()) {
            StoreInfoDTO infoDTO = storeRes.getResponseData();
            return ReturnDataInfo.successData(
                BuildingDTO.toDto(buildingInfo, infoDTO.getBaseInfo(),
                    infoDTO.getRegionBaseInfo()));
          }
          return ReturnDataInfo.failData(storeRes.getErrorInfo());
        }
      } catch (Exception e) {
        log.error("fail to find building due to db error:{}", e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }
}
