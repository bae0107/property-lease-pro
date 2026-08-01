package com.jugu.propertylease.main.occupancy.service;

import static com.jugu.propertylease.main.jooq.Tables.DOOR_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import com.jugu.propertylease.common.crypto.AesGcmCipher;
import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.occupancy.outer.DoorLockPort;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 门锁密码凭证服务：入住/换宿生成密码（AES-GCM 加密落库），退宿/换宿作废。
 *
 * <p>密码明文只出现在生成瞬间（交给 {@link DoorLockPort} 下发）和租客查询解密时，
 * 不落日志、不进管理台响应。device-service 契约就绪后仅需替换 Port 实现。
 */
@Service
public class DoorCredentialService {

    private static final Logger log = LoggerFactory.getLogger(DoorCredentialService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DSLContext dsl;
    private final DoorLockPort doorLockPort;
    private final AesGcmCipher cipher;

    public DoorCredentialService(DSLContext dsl,
                                 DoorLockPort doorLockPort,
                                 @Value("${occupancy.door-credential.aes-key}") String aesKey) {
        this.dsl = dsl;
        this.doorLockPort = doorLockPort;
        this.cipher = new AesGcmCipher(aesKey);
    }

    public record ActiveDoorPassword(Long stayId, Long roomId, String password,
                                     OffsetDateTime issuedAt) {}

    /**
     * 入住/换宿：该 stay 旧凭证（若有）作废，生成 8 位数字新密码，加密落库 ACTIVE 并下发。
     *
     * <p>iamUserId 为 null（历史遗留 stay 未绑定小程序账号）时跳过生成，仅记 WARN。
     */
    public void issueForStay(Long stayId, Long roomId, Long tenantId, Long iamUserId) {
        if (iamUserId == null) {
            log.warn("stayId={} 未绑定 iam_user_id，跳过门锁密码生成", stayId);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        revokeActiveByStay(stayId, now);

        String plain = String.format("%08d", RANDOM.nextInt(100_000_000));
        dsl.insertInto(DOOR_CREDENTIAL)
                .set(DOOR_CREDENTIAL.STAY_ID, stayId)
                .set(DOOR_CREDENTIAL.ROOM_ID, roomId)
                .set(DOOR_CREDENTIAL.TENANT_ID, tenantId)
                .set(DOOR_CREDENTIAL.IAM_USER_ID, iamUserId)
                .set(DOOR_CREDENTIAL.PASSWORD_ENC, cipher.encrypt(plain))
                .set(DOOR_CREDENTIAL.STATUS, "ACTIVE")
                .set(DOOR_CREDENTIAL.CREATED_AT, now)
                .execute();

        doorLockPort.issueCredential(tenantId, roomId, stayId, plain);
    }

    /** 退宿/换宿：该 stay 的 ACTIVE 凭证置 REVOKED，并通知门锁回收。 */
    public void revokeForStay(Long stayId, Long roomId, Long tenantId) {
        int revoked = revokeActiveByStay(stayId, OffsetDateTime.now());
        if (revoked > 0) {
            doorLockPort.revokeCredential(tenantId, roomId, stayId);
        }
    }

    /** 租客小程序查询：该 IAM 用户名下全部 ACTIVE 凭证，解密返回。 */
    public List<ActiveDoorPassword> findActivePasswordsByIamUser(Long iamUserId) {
        return dsl.select(DOOR_CREDENTIAL.STAY_ID, DOOR_CREDENTIAL.ROOM_ID,
                        DOOR_CREDENTIAL.PASSWORD_ENC, DOOR_CREDENTIAL.CREATED_AT)
                .from(DOOR_CREDENTIAL)
                .where(DOOR_CREDENTIAL.IAM_USER_ID.eq(iamUserId)
                        .and(DOOR_CREDENTIAL.STATUS.eq("ACTIVE")))
                .orderBy(DOOR_CREDENTIAL.CREATED_AT.desc())
                .fetch(r -> new ActiveDoorPassword(
                        r.get(DOOR_CREDENTIAL.STAY_ID),
                        r.get(DOOR_CREDENTIAL.ROOM_ID),
                        cipher.decrypt(r.get(DOOR_CREDENTIAL.PASSWORD_ENC)),
                        r.get(DOOR_CREDENTIAL.CREATED_AT)));
    }

    /**
     * 租客自助查询入口：先校验调用方为未删除的 TENANT 用户，再返回其 ACTIVE 密码。
     *
     * @throws BusinessException 403 非 TENANT 用户（含 STAFF / 已注销账号）
     */
    public List<ActiveDoorPassword> findActivePasswordsForTenant(Long iamUserId) {
        String userType = dsl.select(IAM_USER.USER_TYPE)
                .from(IAM_USER)
                .where(IAM_USER.ID.eq(iamUserId).and(IAM_USER.DELETED_AT.isNull()))
                .fetchOne(IAM_USER.USER_TYPE);
        if (!"TENANT".equals(userType)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "OCCUPANCY_TENANT_ONLY", "仅租客用户可查询门锁密码");
        }
        return findActivePasswordsByIamUser(iamUserId);
    }

    private int revokeActiveByStay(Long stayId, OffsetDateTime now) {
        return dsl.update(DOOR_CREDENTIAL)
                .set(DOOR_CREDENTIAL.STATUS, "REVOKED")
                .set(DOOR_CREDENTIAL.REVOKED_AT, now)
                .where(DOOR_CREDENTIAL.STAY_ID.eq(stayId)
                        .and(DOOR_CREDENTIAL.STATUS.eq("ACTIVE")))
                .execute();
    }
}
