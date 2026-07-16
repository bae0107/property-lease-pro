package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.occupancy.api.StayInfo;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link OccupancyQueryPort} 实现，直接委托 {@link OccupancyRepository}。
 */
@Service
public class OccupancyQueryPortImpl implements OccupancyQueryPort {

    private final OccupancyRepository repo;

    public OccupancyQueryPortImpl(OccupancyRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Long> findRoomIdsWithCheckedInStays(LocalDate date) {
        return repo.findRoomIdsWithCheckedInStays(date);
    }

    @Override
    public List<StayInfo> getCheckedInStaysByRoom(Long roomId) {
        return repo.findCheckedInStaysByRoom(roomId).stream()
                .map(this::toStayInfo)
                .toList();
    }

    @Override
    public boolean hasCheckedInStays(Long roomId) {
        return repo.hasCheckedInStays(roomId);
    }

    @Override
    public StayInfo getStay(Long stayId) {
        return repo.findStayById(stayId)
                .map(this::toStayInfo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "STAY_NOT_FOUND", "入住记录不存在：" + stayId));
    }

    @Override
    public int countAssignedByRoom(Long roomId) {
        return repo.countAssignedByRoom(roomId);
    }

    @Override
    public int countCheckedInByRoom(Long roomId) {
        return repo.countCheckedInByRoom(roomId);
    }

    private StayInfo toStayInfo(Stay stay) {
        return new StayInfo(
                stay.getId(),
                stay.getContractId(),
                stay.getContractRoomId(),
                stay.getRoomId(),
                stay.getTenantId(),
                stay.getStayStatus(),
                stay.getCheckInAt()
        );
    }
}
