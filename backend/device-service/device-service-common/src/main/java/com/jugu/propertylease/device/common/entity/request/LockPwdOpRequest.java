package com.jugu.propertylease.device.common.entity.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智能锁密码相关操作")
public class LockPwdOpRequest {

  @Schema(description = "智能锁主键id")
  private long id;

  @Schema(description = "操作密码id")
  private String pwdId;
}
