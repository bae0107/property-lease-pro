package com.jugu.propertylease.main.propertymgr.store;

import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreBaseInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreFilter;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreInfoDTO;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreStrategyInfo;
import java.util.List;

public interface StoreServiceI {

  ReturnInfo createStore(StoreBaseInfo storeBaseInfo, String userName, String userId);

  ReturnInfo updateStoreBase(StoreBaseInfo storeBaseInfo, String userName, String userId);

  ReturnDataInfo<StoreInfoDTO> findStoreById(long id);

  ReturnInfo deleteStore(long id, String userName, String userId);

  ReturnInfo updateStrategyInfo(StoreStrategyInfo strategyInfo, String userName, String userId,
      long id);

  ReturnDataInfo<List<StoreInfoDTO>> findStoresByFilter(StoreFilter storeFilter);
}
