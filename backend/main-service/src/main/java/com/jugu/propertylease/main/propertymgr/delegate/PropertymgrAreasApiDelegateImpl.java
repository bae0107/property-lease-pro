package com.jugu.propertylease.main.propertymgr.delegate;

import com.jugu.propertylease.main.api.PropertymgrAreasApiDelegate;
import com.jugu.propertylease.main.api.model.Area;
import com.jugu.propertylease.main.api.model.AreaPageResult;
import com.jugu.propertylease.main.api.model.AreaQueryRequest;
import com.jugu.propertylease.main.api.model.CreateAreaRequest;
import com.jugu.propertylease.main.api.model.UpdateAreaRequest;
import com.jugu.propertylease.main.propertymgr.service.PropertymgrService;
import org.springframework.stereotype.Service;

@Service
public class PropertymgrAreasApiDelegateImpl implements PropertymgrAreasApiDelegate {

    private final PropertymgrService propertymgrService;

    public PropertymgrAreasApiDelegateImpl(PropertymgrService propertymgrService) {
        this.propertymgrService = propertymgrService;
    }

    @Override
    public Area createArea(CreateAreaRequest request) {
        return toApiModel(propertymgrService.createArea(request.getAreaName()));
    }

    @Override
    public Area updateArea(Long id, UpdateAreaRequest request) {
        return toApiModel(propertymgrService.updateArea(id, request.getAreaName()));
    }

    @Override
    public AreaPageResult queryAreas(AreaQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;

        var result = propertymgrService.queryAreas(request.getAreaName(), page, size);

        return new AreaPageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total((long) result.total())
                .pageNo(page)
                .pageSize(size);
    }

    private Area toApiModel(PropertymgrService.AreaInfo info) {
        return new Area()
                .areaId(info.areaId())
                .areaName(info.areaName());
    }
}
