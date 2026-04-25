package com.jugu.propertylease.main.iam.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUserDataScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserDtoMapperTest {

  private final UserDtoMapper mapper = new UserDtoMapper(new RoleDtoMapper());

  @Test
  void toUserDetail_prefersAllScopeOverSpecificForSameDimension() {
    IamUser user = new IamUser();
    user.setId(1L);
    user.setUserName("u1");
    user.setRealName("测试用户");
    user.setMobile("13800000000");
    user.setEmail("u1@example.com");
    user.setUserType(UserType.STAFF.getValue());
    user.setSourceType(SourceType.CUSTOM.getValue());
    user.setStatus(UserStatus.ACTIVE.getValue());
    user.setSource("MANUAL");
    user.setAuthVersion(2);

    IamRole role = new IamRole();
    role.setId(9L);
    role.setName("店员");
    role.setCode("ROLE_STAFF");
    role.setRoleType(RoleType.STAFF.getValue());
    role.setSourceType(SourceType.CUSTOM.getValue());

    IamUserDataScope areaSpecific = new IamUserDataScope();
    areaSpecific.setScopeDimension(DataScopeDimension.AREA.getValue());
    areaSpecific.setScopeType(DataScopeType.SPECIFIC.getValue());
    areaSpecific.setResourceId(11L);

    IamUserDataScope areaAll = new IamUserDataScope();
    areaAll.setScopeDimension(DataScopeDimension.AREA.getValue());
    areaAll.setScopeType(DataScopeType.ALL.getValue());
    areaAll.setResourceId(null);

    var detail = mapper.toUserDetail(user, List.of(role), List.of(areaSpecific, areaAll));

    assertThat(detail.getRoles()).hasSize(1);
    assertThat(detail.getRoleNames()).isEqualTo("店员");
    assertThat(detail.getDataScope()).isNotNull();
    assertThat(detail.getDataScope().getScopes()).hasSize(1);
    assertThat(detail.getDataScope().getScopes().get(0).getDimension()).isEqualTo(DataScopeDimension.AREA);
    assertThat(detail.getDataScope().getScopes().get(0).getScopeType()).isEqualTo(DataScopeType.ALL);
    assertThat(detail.getDataScope().getScopes().get(0).getResourceIds()).isNull();
  }
}
