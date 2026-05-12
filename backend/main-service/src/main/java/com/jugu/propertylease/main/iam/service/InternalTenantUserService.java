package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import com.jugu.propertylease.main.internal.api.model.CreateTenantRequest;
import com.jugu.propertylease.main.internal.api.model.CreateTenantResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 内部 TENANT 用户预创建服务（/internal/v1/users）。
 *
 * <p>由业务系统（billing-service / device-service）在创建租客业务实体时调用。
 * 创建时仅建立用户记录，不涉及登录凭证和 Identity，
 * 待 TENANT 首次微信登录时通过手机号匹配绑定 Identity。
 *
 * <p>格式校验（mobile 非空、pattern）由 OpenAPI yaml @Valid 在框架层完成，
 * Service 层不重复 null 判断。
 */
@Service
public class InternalTenantUserService {

    private final UserRepository userRepo;

    public InternalTenantUserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional
    public CreateTenantResult createTenantUser(CreateTenantRequest request) {
        // userName 规则：tenant_ + mobile，保证唯一且可读
        String userName = "tenant_" + request.getMobile();
        OffsetDateTime now = OffsetDateTime.now();

        try {
            Long userId = userRepo.insert(
                    UserType.TENANT,
                    userName,
                    /* realName */ null,
                    request.getMobile(),
                    /* email */ null,
                    now
            );
            return new CreateTenantResult()
                    .userId(userId)
                    .mobile(request.getMobile())
                    .type(CreateTenantResult.TypeEnum.TENANT)
                    .status(CreateTenantResult.StatusEnum.ACTIVE);
        } catch (DuplicateKeyException ex) {
            // mobile 唯一约束冲突：同一手机号不允许重复创建
            throw new BusinessException(HttpStatus.CONFLICT,
                    "IAM_TENANT_MOBILE_DUPLICATE", "手机号已被使用");
        }
    }
}
