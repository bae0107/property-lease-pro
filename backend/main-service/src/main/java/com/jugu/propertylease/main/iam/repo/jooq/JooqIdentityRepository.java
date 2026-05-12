package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;

import com.jugu.propertylease.main.iam.auth.IdentityProvider;
import com.jugu.propertylease.main.iam.repo.IdentityRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class JooqIdentityRepository implements IdentityRepository {

    private final DSLContext dsl;

    public JooqIdentityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insertPasswordIdentity(Long userId, String username, OffsetDateTime now) {
        dsl.insertInto(IAM_IDENTITY)
                .set(IAM_IDENTITY.USER_ID, userId)
                .set(IAM_IDENTITY.PROVIDER, IdentityProvider.PASSWORD.value())
                .set(IAM_IDENTITY.PROVIDER_USER_ID, username)
                .set(IAM_IDENTITY.UNION_ID, (String) null)
                .set(IAM_IDENTITY.APP_ID, (String) null)
                .set(IAM_IDENTITY.CREATED_AT, now)
                .set(IAM_IDENTITY.DELETED_AT, (OffsetDateTime) null)
                .execute();
    }

    @Override
    public void softDeleteAllByUserId(Long userId, OffsetDateTime now) {
        dsl.update(IAM_IDENTITY)
                .set(IAM_IDENTITY.DELETED_AT, now)
                .where(IAM_IDENTITY.USER_ID.eq(userId))
                .and(IAM_IDENTITY.DELETED_AT.isNull())
                .execute();
    }
}
