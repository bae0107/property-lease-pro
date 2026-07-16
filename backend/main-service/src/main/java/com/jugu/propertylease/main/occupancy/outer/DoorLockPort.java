package com.jugu.propertylease.main.occupancy.outer;

/**
 * 门锁控制 Port（供 occupancy 在入住/退宿/换宿时注入调用）。
 *
 * <p>当前为 stub 实现，不发起真实 device-service 调用。
 * 待 device-service 提供 OpenAPI 规范后，新建 {@code HttpDoorLockPort} 替换，
 * 调用方（occupancy Service 层）代码无需变动。
 */
public interface DoorLockPort {

    /**
     * 入住/换宿时下发门锁凭证（授权该 tenant 可开锁）。
     *
     * @param tenantId 员工 ID（即 customer.employee.id）
     * @param roomId   房间 ID
     * @param stayId   stay.id（凭证与入住记录绑定，便于精确回收）
     */
    void issueCredential(Long tenantId, Long roomId, Long stayId);

    /**
     * 退宿/换宿时回收门锁凭证（注销该 tenant 的开锁权限）。
     */
    void revokeCredential(Long tenantId, Long roomId, Long stayId);
}
