package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;

import com.jugu.propertylease.main.iam.repo.CredentialRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class JooqCredentialRepository implements CredentialRepository {

    private final DSLContext dsl;

    public JooqCredentialRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现原子 upsert。
     *
     * <p>iam_credential.user_id 是主键，冲突即为已存在，直接 UPDATE。
     * 调用方无需在 Service 层先查再判断，消除 credentialExists() 的必要性。
     */
    @Override
    public void upsert(Long userId, String passwordHash, OffsetDateTime now) {
        dsl.insertInto(IAM_CREDENTIAL)
                .set(IAM_CREDENTIAL.USER_ID, userId)
                .set(IAM_CREDENTIAL.PASSWORD_HASH, passwordHash)
                .set(IAM_CREDENTIAL.CREATED_AT, now)
                .set(IAM_CREDENTIAL.UPDATED_AT, now)
                .onDuplicateKeyUpdate()
                .set(IAM_CREDENTIAL.PASSWORD_HASH, passwordHash)
                .set(IAM_CREDENTIAL.UPDATED_AT, now)
                .execute();
    }
}
