package com.jugu.propertylease.main.iam.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import org.junit.jupiter.api.Test;

class RoleDtoMapperTest {

  private final RoleDtoMapper mapper = new RoleDtoMapper();

  @Test
  void toRole_mapsEnumAndBasicFields() {
    IamRole role = new IamRole();
    role.setId(101L);
    role.setName("运营角色");
    role.setCode("ROLE_OPS");
    role.setRoleType(RoleType.STAFF.getValue());
    role.setSourceType(SourceType.CUSTOM.getValue());
    role.setRequiredDataScopeDimension(DataScopeDimension.AREA.getValue());
    role.setDescription("ops");

    var dto = mapper.toRole(role);

    assertThat(dto.getId()).isEqualTo(101L);
    assertThat(dto.getName()).isEqualTo("运营角色");
    assertThat(dto.getCode()).isEqualTo("ROLE_OPS");
    assertThat(dto.getRoleType()).isEqualTo(RoleType.STAFF);
    assertThat(dto.getSourceType()).isEqualTo(SourceType.CUSTOM);
    assertThat(dto.getRequiredDataScopeDimension()).isEqualTo(DataScopeDimension.AREA);
    assertThat(dto.getDescription()).isEqualTo("ops");
  }
}
