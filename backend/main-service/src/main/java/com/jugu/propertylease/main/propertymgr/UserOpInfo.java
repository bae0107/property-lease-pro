package com.jugu.propertylease.main.propertymgr;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true)
public class UserOpInfo {

  @Schema(description = "创建人Id")
  private String createUserId;

  @Schema(description = "创建人名称")
  private String createUserName;

  @Schema(description = "创建时间")
  private String createTime;

  @Schema(description = "更新人Id")
  private String updateUserId;

  @Schema(description = "更新人姓名")
  private String updateUserName;

  @Schema(description = "更新时间")
  private String updateTime;

  @Schema(description = "删除标记(1=正常、2=删除)")
  private int isDeleted;

  @Schema(description = "删除人Id")
  private String deleteUserId;

  @Schema(description = "删除人名称")
  private String deleteUserName;

  @Schema(description = "删除时间")
  private String deleteTime;
}
