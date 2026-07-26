package com.jugu.propertylease.main.propertymgr.delegate;

import com.jugu.propertylease.main.api.PropertymgrBuildingsApiDelegate;
import com.jugu.propertylease.main.api.model.Building;
import com.jugu.propertylease.main.api.model.BuildingPageResult;
import com.jugu.propertylease.main.api.model.BuildingQueryRequest;
import com.jugu.propertylease.main.api.model.CreateBuildingRequest;
import com.jugu.propertylease.main.propertymgr.service.PropertymgrService;
import org.springframework.stereotype.Service;

@Service
public class PropertymgrBuildingsApiDelegateImpl implements PropertymgrBuildingsApiDelegate {

    private final PropertymgrService propertymgrService;

    public PropertymgrBuildingsApiDelegateImpl(PropertymgrService propertymgrService) {
        this.propertymgrService = propertymgrService;
    }

    @Override
    public Building createBuilding(CreateBuildingRequest request) {
        return toApiModel(propertymgrService.createBuilding(
                request.getBuildingId(),
                request.getStoreId(),
                request.getBuildingName()));
    }

    @Override
    public BuildingPageResult queryBuildings(BuildingQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;

        var result = propertymgrService.queryBuildings(request.getBuildingName(), page, size);

        return new BuildingPageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total((long) result.total())
                .pageNo(page)
                .pageSize(size);
    }

    private Building toApiModel(PropertymgrService.BuildingInfo info) {
        return new Building()
                .buildingId(info.buildingId())
                .storeId(info.storeId())
                .buildingName(info.buildingName());
    }
}
