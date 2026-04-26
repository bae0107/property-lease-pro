package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.internal.api.model.CreateTenantRequest;
import com.jugu.propertylease.main.internal.api.model.CreateTenantResult;
import com.jugu.propertylease.main.iam.repo.IamUserMutationRepository;
import java.time.OffsetDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 内部 TENANT 用户创建服务。
 */
@Service
public class InternalTenantUserService {

  private static final String DEFAULT_SOURCE = "INTERNAL_API";

  private final IamUserMutationRepository userMutationRepository;

  public InternalTenantUserService(IamUserMutationRepository userMutationRepository) {
    this.userMutationRepository = userMutationRepository;
  }

  @Transactional
  public CreateTenantResult createTenantUser(CreateTenantRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_INTERNAL_TENANT_REQUEST_REQUIRED", "请求体不能为空");
    }

    String source = StringUtils.hasText(request.getSource()) ? request.getSource() : DEFAULT_SOURCE;
    String userName = "tenant_" + request.getMobile();
    OffsetDateTime now = OffsetDateTime.now();

    try {
      Long userId = userMutationRepository.insertUser("TENANT", userName, null, request.getMobile(), null,
          source, now);
      return new CreateTenantResult()
          .userId(userId)
          .mobile(request.getMobile())
          .type(CreateTenantResult.TypeEnum.TENANT)
          .status(CreateTenantResult.StatusEnum.ACTIVE);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "IAM_TENANT_MOBILE_DUPLICATE", "手机号已被使用");
    }
  }
}
