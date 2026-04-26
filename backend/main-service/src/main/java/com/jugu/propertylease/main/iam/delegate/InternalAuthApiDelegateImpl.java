package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.internal.api.InternalAuthApiDelegate;
import com.jugu.propertylease.main.internal.api.model.AuthVersionCheckResult;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InternalAuthApiDelegateImpl implements InternalAuthApiDelegate {

  private final AuthVersionService authVersionService;

  public InternalAuthApiDelegateImpl(AuthVersionService authVersionService) {
    this.authVersionService = authVersionService;
  }

  @Override
  public AuthVersionCheckResult checkAuthVersion(Long userId, Integer authVersion) {
    Integer currentVersion = authVersionService.getCurrentAuthVersion(userId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在"));

    return new AuthVersionCheckResult()
        .userId(userId)
        .tokenAuthVersion(authVersion)
        .currentAuthVersion(currentVersion)
        .current(Objects.equals(currentVersion, authVersion));
  }
}
