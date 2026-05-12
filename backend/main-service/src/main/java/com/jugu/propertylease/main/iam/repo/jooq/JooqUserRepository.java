package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JooqUserRepository implements UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Long insert(UserType userType, String userName, String realName,
                       String mobile, String email, OffsetDateTime now) {
        return dsl.insertInto(IAM_USER)
                .set(IAM_USER.USER_TYPE, userType.getValue())
                .set(IAM_USER.SOURCE_TYPE, SourceType.CUSTOM.getValue())
                .set(IAM_USER.SOURCE, "MANUAL")
                .set(IAM_USER.STATUS, UserStatus.ACTIVE.getValue())
                .set(IAM_USER.AUTH_VERSION, 0)
                .set(IAM_USER.USER_NAME, userName)
                .set(IAM_USER.REAL_NAME, realName)
                .set(IAM_USER.MOBILE, mobile)
                .set(IAM_USER.EMAIL, email)
                .set(IAM_USER.CREATED_BY, (Long) null)
                .set(IAM_USER.CREATED_AT, now)
                .set(IAM_USER.UPDATED_AT, now)
                .returning(IAM_USER.ID)
                .fetchOne(IAM_USER.ID);
    }

    @Override
    public Optional<IamUser> findActiveById(Long userId) {
        return Optional.ofNullable(
                dsl.selectFrom(IAM_USER)
                        .where(IAM_USER.ID.eq(userId))
                        .and(IAM_USER.DELETED_AT.isNull())
                        .fetchOneInto(IamUser.class)
        );
    }

    @Override
    public Optional<UserBaseInfo> findActiveBaseById(Long userId) {
        return dsl.select(IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE)
                .from(IAM_USER)
                .where(IAM_USER.ID.eq(userId))
                .and(IAM_USER.DELETED_AT.isNull())
                .fetchOptional(r -> new UserBaseInfo(
                        UserType.fromValue(r.get(IAM_USER.USER_TYPE)),
                        SourceType.fromValue(r.get(IAM_USER.SOURCE_TYPE))
                ));
    }

    @Override
    public void updateProfile(Long userId, String realName, String mobile,
                              String email, OffsetDateTime now) {
        dsl.update(IAM_USER)
                .set(IAM_USER.REAL_NAME, realName)
                .set(IAM_USER.MOBILE, mobile)
                .set(IAM_USER.EMAIL, email)
                .set(IAM_USER.UPDATED_AT, now)
                .where(IAM_USER.ID.eq(userId))
                .and(IAM_USER.DELETED_AT.isNull())
                .execute();
    }

    @Override
    public void updateStatus(Long userId, UserStatus status, OffsetDateTime now) {
        dsl.update(IAM_USER)
                .set(IAM_USER.STATUS, status.getValue())
                .set(IAM_USER.UPDATED_AT, now)
                .where(IAM_USER.ID.eq(userId))
                .and(IAM_USER.DELETED_AT.isNull())
                .execute();
    }

    @Override
    public void batchUpdateStatus(List<Long> userIds, UserStatus status, OffsetDateTime now) {
        // 单条 SQL 完成批量更新，避免 N+1
        dsl.update(IAM_USER)
                .set(IAM_USER.STATUS, status.getValue())
                .set(IAM_USER.UPDATED_AT, now)
                .where(IAM_USER.ID.in(userIds))
                .and(IAM_USER.DELETED_AT.isNull())
                .execute();
    }
}
