package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.iam.repo.IamUserReadRepository;
import com.jugu.propertylease.main.iam.service.mapper.UserDtoMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 用户读取聚合服务。
 */
@Service
public class UserReadService {

  private final IamUserReadRepository userReadRepository;
  private final UserDtoMapper userDtoMapper;

  public UserReadService(IamUserReadRepository userReadRepository, UserDtoMapper userDtoMapper) {
    this.userReadRepository = userReadRepository;
    this.userDtoMapper = userDtoMapper;
  }

  public UserDetail getUserDetail(Long userId) {
    var user = userReadRepository.findActiveUserById(userId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在"));

    var roles = userReadRepository.findRolesByUserId(userId);
    var scopeRows = userReadRepository.findDataScopesByUserId(userId);

    return userDtoMapper.toUserDetail(user, roles, scopeRows);
  }
}
