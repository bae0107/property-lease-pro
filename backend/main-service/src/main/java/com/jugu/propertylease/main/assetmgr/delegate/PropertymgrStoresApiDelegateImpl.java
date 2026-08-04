package com.jugu.propertylease.main.assetmgr.delegate;

import com.jugu.propertylease.main.api.PropertymgrStoresApiDelegate;
import com.jugu.propertylease.main.api.model.CreateStoreRequest;
import com.jugu.propertylease.main.api.model.Store;
import com.jugu.propertylease.main.api.model.StorePageResult;
import com.jugu.propertylease.main.api.model.StoreQueryRequest;
import com.jugu.propertylease.main.api.model.UpdateStoreRequest;
import com.jugu.propertylease.main.assetmgr.service.PropertymgrService;
import org.springframework.stereotype.Service;

@Service
public class PropertymgrStoresApiDelegateImpl implements PropertymgrStoresApiDelegate {

    private final PropertymgrService propertymgrService;

    public PropertymgrStoresApiDelegateImpl(PropertymgrService propertymgrService) {
        this.propertymgrService = propertymgrService;
    }

    @Override
    public Store createStore(CreateStoreRequest request) {
        return toApiModel(propertymgrService.createStore(
                request.getAreaId(), request.getStoreName()));
    }

    @Override
    public Store updateStore(Long id, UpdateStoreRequest request) {
        return toApiModel(propertymgrService.updateStore(
                id, request.getAreaId(), request.getStoreName()));
    }

    @Override
    public StorePageResult queryStores(StoreQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;

        var result = propertymgrService.queryStores(
                request.getAreaId(), request.getStoreName(), page, size);

        return new StorePageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total((long) result.total())
                .pageNo(page)
                .pageSize(size);
    }

    private Store toApiModel(PropertymgrService.StoreInfo info) {
        return new Store()
                .storeId(info.storeId())
                .areaId(info.areaId())
                .storeName(info.storeName());
    }
}
