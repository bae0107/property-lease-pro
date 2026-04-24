package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUserDataScope;
import java.util.List;
import java.util.Optional;

public interface IamUserReadRepository {

  Optional<IamUser> findActiveUserById(Long userId);

  List<IamRole> findRolesByUserId(Long userId);

  List<IamUserDataScope> findDataScopesByUserId(Long userId);
}
