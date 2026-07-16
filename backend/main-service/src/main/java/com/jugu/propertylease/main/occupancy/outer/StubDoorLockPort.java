package com.jugu.propertylease.main.occupancy.outer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link DoorLockPort} 的 stub 实现。
 *
 * <p>所有方法仅打印 WARN 日志，不发起真实网络调用。
 * 替换方式：待 device-service 契约就绪后，新建
 * {@code HttpDoorLockPort implements DoorLockPort} 并通过 Spring Profile 切换。
 */
@Service
public class StubDoorLockPort implements DoorLockPort {

    private static final Logger log = LoggerFactory.getLogger(StubDoorLockPort.class);

    @Override
    public void issueCredential(Long tenantId, Long roomId, Long stayId) {
        log.warn("[STUB-DOORLOCK] issueCredential: tenantId={} roomId={} stayId={}",
                tenantId, roomId, stayId);
    }

    @Override
    public void revokeCredential(Long tenantId, Long roomId, Long stayId) {
        log.warn("[STUB-DOORLOCK] revokeCredential: tenantId={} roomId={} stayId={}",
                tenantId, roomId, stayId);
    }
}
