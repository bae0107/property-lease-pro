package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import com.jugu.propertylease.main.iam.repo.model.UserSoftDeleteCommand;
import java.time.OffsetDateTime;

public interface IamUserLifecycleRepository {

  UserDeleteSnapshot findActiveUserSnapshot(Long userId);

  void softDeleteUser(UserSoftDeleteCommand command);

  void markIdentityDeleted(Long userId, OffsetDateTime now);

  boolean isUserAssignedRoleCode(Long userId, String roleCode);

  int countActiveUsersByRoleCode(String roleCode);
}
