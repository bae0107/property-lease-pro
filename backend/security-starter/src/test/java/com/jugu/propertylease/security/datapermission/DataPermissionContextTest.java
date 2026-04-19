package com.jugu.propertylease.security.datapermission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataPermissionContextTest {

  @AfterEach
  void clear() {
    DataPermissionContext.clear();
  }

  @Test
  void set_and_get_returnsValue() {
    DataPermissionContext.set(42L);
    assertThat(DataPermissionContext.get()).isEqualTo(42L);
  }

  @Test
  void clear_removesValue() {
    DataPermissionContext.set(1L);
    DataPermissionContext.clear();
    assertThat(DataPermissionContext.get()).isNull();
  }

  @Test
  void get_withoutSet_returnsNull() {
    assertThat(DataPermissionContext.get()).isNull();
  }

  @Test
  void set_null_isAllowed() {
    DataPermissionContext.set(null);
    assertThat(DataPermissionContext.get()).isNull();
  }
}
