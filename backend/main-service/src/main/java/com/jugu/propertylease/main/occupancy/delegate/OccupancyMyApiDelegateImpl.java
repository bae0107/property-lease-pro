package com.jugu.propertylease.main.occupancy.delegate;

import com.jugu.propertylease.main.api.OccupancyMyApiDelegate;
import com.jugu.propertylease.main.api.model.DoorPasswordItem;
import com.jugu.propertylease.main.api.model.DoorPasswordResult;
import com.jugu.propertylease.main.occupancy.service.DoorCredentialService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class OccupancyMyApiDelegateImpl implements OccupancyMyApiDelegate {

    private final DoorCredentialService doorCredentialService;

    public OccupancyMyApiDelegateImpl(DoorCredentialService doorCredentialService) {
        this.doorCredentialService = doorCredentialService;
    }

    @Override
    public DoorPasswordResult getMyDoorPassword() {
        Long userId = CurrentUser.getCurrentUserId();
        return new DoorPasswordResult().items(
                doorCredentialService.findActivePasswordsForTenant(userId).stream()
                        .map(p -> new DoorPasswordItem()
                                .stayId(p.stayId())
                                .roomId(p.roomId())
                                .password(p.password())
                                .issuedAt(p.issuedAt()))
                        .toList());
    }
}
