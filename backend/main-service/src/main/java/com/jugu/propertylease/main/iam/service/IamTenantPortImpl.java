package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * {@link IamTenantPort} 实现。
 *
 * <p>注意：不复用 {@code InternalTenantUserService}，
 * 那个服务服务于"预创建"场景（重复则报 409），本实现是"幂等查找或创建"语义。
 */
@Service
public class IamTenantPortImpl implements IamTenantPort {

    private final UserRepository userRepo;
    private final UserLifecycleService userLifecycleService;

    public IamTenantPortImpl(UserRepository userRepo,
                              UserLifecycleService userLifecycleService) {
        this.userRepo = userRepo;
        this.userLifecycleService = userLifecycleService;
    }

    /**
     * 幂等创建/查找 TENANT 用户。
     * 按 mobile 查到已有用户则复用（不重建），未找到则新建。
     */
    @Override
    @Transactional
    public Long createOrEnableTenantUser(String mobile, String realName) {
        return userRepo.findActiveByMobileAndType(mobile, UserType.TENANT.getValue())
                .map(user -> user.getId())
                .orElseGet(() -> {
                    String userName = "tenant_" + mobile;
                    return userRepo.insert(
                            UserType.TENANT,
                            userName,
                            realName,
                            mobile,
                            null,           // email
                            OffsetDateTime.now()
                    );
                });
    }

    /**
     * 退宿后软删除 IAM 用户，注销小程序登录能力。
     * operatorUserId 传 null = 系统自动操作。
     */
    @Override
    @Transactional
    public void disableTenantUser(Long iamUserId) {
        userLifecycleService.softDeleteUser(iamUserId, null, "退宿自动注销");
    }
}
