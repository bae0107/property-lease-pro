package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamCredential;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import java.util.List;
import java.util.Optional;

public interface IamAuthQueryRepository {

  Optional<Long> findActivePasswordIdentityUserId(String username);

  Optional<IamUser> findActiveUserById(Long userId);

  Optional<IamCredential> findCredentialByUserId(Long userId);

  List<String> findPermissionCodesByUserId(Long userId);
}
