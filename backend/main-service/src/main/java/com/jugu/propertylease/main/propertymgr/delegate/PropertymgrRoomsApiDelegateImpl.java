package com.jugu.propertylease.main.propertymgr.delegate;

import com.jugu.propertylease.main.api.PropertymgrRoomsApiDelegate;
import com.jugu.propertylease.main.api.model.CreateRoomRequest;
import com.jugu.propertylease.main.api.model.Room;
import com.jugu.propertylease.main.api.model.RoomPageResult;
import com.jugu.propertylease.main.api.model.RoomQueryRequest;
import com.jugu.propertylease.main.propertymgr.service.PropertymgrService;
import org.springframework.stereotype.Service;

@Service
public class PropertymgrRoomsApiDelegateImpl implements PropertymgrRoomsApiDelegate {

    private final PropertymgrService propertymgrService;

    public PropertymgrRoomsApiDelegateImpl(PropertymgrService propertymgrService) {
        this.propertymgrService = propertymgrService;
    }

    @Override
    public Room createRoom(CreateRoomRequest request) {
        return toApiModel(propertymgrService.createRoom(
                request.getBuildingId(),
                request.getLevel(),
                request.getRoomNum(),
                request.getLivingNum()));
    }

    @Override
    public RoomPageResult queryRooms(RoomQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;
        String roomStatus = request.getRoomStatus() != null
                ? request.getRoomStatus().getValue() : null;

        var result = propertymgrService.queryRooms(
                request.getBuildingId(), roomStatus, page, size);

        return new RoomPageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total((long) result.total())
                .pageNo(page)
                .pageSize(size);
    }

    private Room toApiModel(PropertymgrService.RoomInfo info) {
        return new Room()
                .roomId(info.roomId())
                .buildingId(info.buildingId())
                .level(info.level())
                .roomNum(info.roomNum())
                .livingNum(info.livingNum())
                .roomStatus(info.roomStatus());
    }
}
