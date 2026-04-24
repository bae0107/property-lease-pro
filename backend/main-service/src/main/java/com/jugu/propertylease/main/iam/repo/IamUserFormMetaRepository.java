package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.util.List;

public interface IamUserFormMetaRepository {

  List<IamRole> findStaffRoles();
}
