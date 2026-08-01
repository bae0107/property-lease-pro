package com.jugu.propertylease.main.occupancy.service;

import static com.jugu.propertylease.main.jooq.Tables.DOOR_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.occupancy.outer.DoorLockPort;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DoorCredentialService 状态流转：生成（8 位数字 + 落库 + 下发）、作废回收、TENANT 校验。
 */
class DoorCredentialServiceTest {

    private static final String AES_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private DSLContext dsl;
    private DoorLockPort port;
    private DoorCredentialService service;

    @BeforeEach
    void setUp() {
        dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
        port = mock(DoorLockPort.class);
        service = new DoorCredentialService(dsl, port, AES_KEY);
    }

    @Test
    void issueForStay_nullIamUserId_skipsGeneration() {
        service.issueForStay(1L, 2L, 3L, null);

        verify(dsl, never()).insertInto(DOOR_CREDENTIAL);
        verify(port, never()).issueCredential(any(), any(), any(), any());
    }

    @Test
    void issueForStay_happyPath_generates8DigitPasswordAndIssues() {
        service.issueForStay(1L, 2L, 3L, 9L);

        verify(dsl).insertInto(DOOR_CREDENTIAL);
        verify(port).issueCredential(eq(3L), eq(2L), eq(1L),
                argThat(p -> p != null && p.matches("\\d{8}")));
    }

    @Test
    void revokeForStay_activeCredentialExists_revokesAndNotifiesPort() {
        when(dsl.update(DOOR_CREDENTIAL)
                .set(eq(DOOR_CREDENTIAL.STATUS), eq("REVOKED"))
                .set(eq(DOOR_CREDENTIAL.REVOKED_AT), any(OffsetDateTime.class))
                .where(any(Condition.class))
                .execute()).thenReturn(1);

        service.revokeForStay(1L, 2L, 3L);

        verify(port).revokeCredential(3L, 2L, 1L);
    }

    @Test
    void revokeForStay_noActiveCredential_skipsPortNotify() {
        // deep stub 默认 execute() = 0（无 ACTIVE 被更新）
        service.revokeForStay(1L, 2L, 3L);

        verify(port, never()).revokeCredential(any(), any(), any());
    }

    @Test
    void findActivePasswordsForTenant_nonTenant_throws403() {
        when(dsl.select(IAM_USER.USER_TYPE)
                .from(IAM_USER)
                .where(any(Condition.class))
                .fetchOne(IAM_USER.USER_TYPE)).thenReturn("STAFF");

        assertThatThrownBy(() -> service.findActivePasswordsForTenant(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("OCCUPANCY_TENANT_ONLY"));
    }

    @Test
    void findActivePasswordsForTenant_deletedOrMissingUser_throws403() {
        // fetchOne 返回 null（账号不存在或已删除）→ 视同非 TENANT
        when(dsl.select(IAM_USER.USER_TYPE)
                .from(IAM_USER)
                .where(any(Condition.class))
                .fetchOne(IAM_USER.USER_TYPE)).thenReturn(null);

        assertThatThrownBy(() -> service.findActivePasswordsForTenant(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("OCCUPANCY_TENANT_ONLY"));
    }

    @Test
    void findActivePasswordsForTenant_tenant_returnsActiveList() {
        when(dsl.select(IAM_USER.USER_TYPE)
                .from(IAM_USER)
                .where(any(Condition.class))
                .fetchOne(IAM_USER.USER_TYPE)).thenReturn("TENANT");

        // deep stub 默认 fetch(...) 返回空 List
        List<DoorCredentialService.ActiveDoorPassword> result =
                service.findActivePasswordsForTenant(9L);

        assertThat(result).isEmpty();
    }
}
