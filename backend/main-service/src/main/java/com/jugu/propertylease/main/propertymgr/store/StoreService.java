package com.jugu.propertylease.main.propertymgr.store;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.jooq.tables.pojos.RegionInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.StoreInfo;
import com.jugu.propertylease.main.propertymgr.area.RegionRepository;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionInfoDTO;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreBaseInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreFilter;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreInfoDTO;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreStrategyInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
@Getter
public class StoreService implements StoreServiceI {

  private final StoreRepository storeRepository;

  private final RegionRepository regionRepository;

  @Override
  public ReturnInfo createStore(StoreBaseInfo storeBaseInfo, String userName, String userId) {
    String regionId = storeBaseInfo.getRegionId();
    if (!Common.isStringInValid(regionId) && !Common.isStringInValid(storeBaseInfo.getStoreName())
        && !Common.isStringInValid(storeBaseInfo.getStoreAddress()) && !Common.isStringInValid(
        storeBaseInfo.getStoreDes())) {
      try {
        int regionCount = regionRepository.countById(regionId);
        if (regionCount > 0) {
          StoreInfo storeInfo = new StoreInfo();
          storeBaseInfo.parseBaseToStore(storeInfo);
          storeInfo.setCreateusername(userName)
              .setCreateuserid(userId)
              .setCreatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          storeRepository.createStore(storeInfo);
          return ReturnInfo.success();
        }
      } catch (Exception e) {
        log.error("fail to create store due to db error:{}", e.getMessage());
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnInfo updateStoreBase(StoreBaseInfo storeBaseInfo, String userName, String userId) {
    String regionId = storeBaseInfo.getRegionId();
    if (!Common.isStringInValid(regionId) && !Common.isStringInValid(storeBaseInfo.getStoreName())
        && !Common.isStringInValid(storeBaseInfo.getStoreAddress()) && !Common.isStringInValid(
        storeBaseInfo.getStoreDes())) {
      try {
        StoreInfo storeInfo = storeRepository.findStoreById(storeBaseInfo.getStoreId());
        if (storeInfo != null) {
          StoreInfoDTO infoDTO = StoreInfoDTO.toDto(storeInfo);
          StoreBaseInfo curBase = infoDTO.getBaseInfo();
          if (curBase.equals(storeBaseInfo)) {
            log.warn("nothing to update for store:{} base info", curBase.getStoreId());
            return ReturnInfo.success();
          }
          if (!regionId.equals(curBase.getRegionId())) {
            int regionCount = regionRepository.countById(regionId);
            if (regionCount == 0) {
              log.error("fail to update store base info due to region invalid:{}", regionId);
              return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
            }
          }
          storeBaseInfo.parseBaseToStore(storeInfo);
          storeInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          storeInfo.setUpdateuserid(userId);
          storeInfo.setUpdateusername(userName);
          storeRepository.update(storeInfo);
          return ReturnInfo.success();
        }
      } catch (Exception e) {
        log.error("fail to update store base info due to db error:{}", e.getMessage());
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnDataInfo<StoreInfoDTO> findStoreById(long id) {
    try {
      StoreInfo storeInfo = storeRepository.findStoreById(id);
      if (storeInfo != null) {
        String regionId = storeInfo.getRegionid();
        if (Common.isStringInValid(regionId)) {
          log.error("fail to find store region id invalid");
          return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域ID缺失");
        }
        RegionInfo regionInfo = regionRepository.findRegionById(regionId);
        if (regionInfo == null) {
          log.error("fail to find store region id:{} info missed", regionId);
          return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域信息缺失");
        }
        StoreInfoDTO storeInfoDTO = StoreInfoDTO.toDto(storeInfo);
        storeInfoDTO.setRegionBaseInfo(RegionInfoDTO.toDto(regionInfo).getRegionBaseInfo());
        return ReturnDataInfo.successData(storeInfoDTO);
      }
      log.error("fail to find store id:{}", id);
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    } catch (Exception e) {
      log.error("fail to find store due to db error:{}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  @Override
  public ReturnInfo deleteStore(long id, String userName, String userId) {
    return null;
  }

  @Override
  public ReturnInfo updateStrategyInfo(StoreStrategyInfo strategyInfo, String userName,
      String userId, long id) {
    if (strategyInfo.checkStrategyLegal()) {
      try {
        StoreInfo storeInfo = storeRepository.findStoreById(id);
        if (storeInfo != null) {
          StoreInfoDTO infoDTO = StoreInfoDTO.toDto(storeInfo);
          StoreStrategyInfo curStrategy = infoDTO.getStoreStrategyInfo();
          if (curStrategy.equals(strategyInfo)) {
            log.warn("nothing to update for store:{} strategy info", id);
            return ReturnInfo.success();
          }
          strategyInfo.parseStrategyToStore(storeInfo);
          storeInfo.setUpdatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          storeInfo.setUpdateuserid(userId);
          storeInfo.setUpdateusername(userName);
          storeRepository.update(storeInfo);
          return ReturnInfo.success();
        }
      } catch (Exception e) {
        log.error("fail to update store strategy info due to db error:{}", e.getMessage());
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Override
  public ReturnDataInfo<List<StoreInfoDTO>> findStoresByFilter(StoreFilter storeFilter) {
    try {
      List<StoreInfo> stores = storeRepository.findStoresByFilter(storeFilter);
      if (Common.isCollectionInValid(stores)) {
        return ReturnDataInfo.successData(new ArrayList<>());
      }
      Set<String> regionIds = new HashSet<>();
      for (StoreInfo storeInfo : stores) {
        String regionId = storeInfo.getRegionid();
        if (Common.isStringInValid(regionId)) {
          log.error("fail to find stores due to region id invalid for:{}", storeInfo.getStoreid());
          return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域ID缺失");
        }
        regionIds.add(regionId);
      }
      if (Common.isCollectionInValid(regionIds)) {
        log.error("fail to find stores region ids invalid");
        return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域IDS缺失");
      }
      List<RegionInfo> regionInfos = regionRepository.findRegionsByIds(regionIds);
      if (Common.isCollectionInValid(regionInfos)) {
        log.error("fail to find stores region ids:{} info missed", regionIds);
        return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域信息缺失");
      }
      Map<String, RegionInfo> regionInfoMap = regionInfos.stream()
          .collect(
              Collectors.toMap(RegionInfo::getRegionid, r -> r, (oldValue, newValue) -> newValue));
      List<StoreInfoDTO> storeInfoDTOS = new ArrayList<>();
      for (StoreInfo storeInfo : stores) {
        StoreInfoDTO dto = StoreInfoDTO.toDto(storeInfo);
        String regionId = storeInfo.getRegionid();
        RegionInfo regionInfo = regionInfoMap.getOrDefault(regionId, null);
        if (regionInfo == null) {
          log.error("fail to find stores due to region info invalid for id:{}", regionId);
          return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "区域信息缺失");
        }
        dto.setRegionBaseInfo(RegionInfoDTO.toDto(regionInfo).getRegionBaseInfo());
        storeInfoDTOS.add(dto);
      }
      return ReturnDataInfo.successData(storeInfoDTOS);
    } catch (Exception e) {
      log.error("fail to find stores due to db error:{}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }
}
