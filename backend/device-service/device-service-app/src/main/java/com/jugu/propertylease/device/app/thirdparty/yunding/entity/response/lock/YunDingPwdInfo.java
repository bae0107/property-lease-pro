package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class YunDingPwdInfo {

  private int id;

  private long time;

  @SerializedName("is_default")
  private int isDefault;

  // 密码当前状态，1：初始状态；2：已生效；3：将在一段时间后生效；4：已过期；5：已冻结。，只有 2 时，密码有效
  @SerializedName("pwd_state")
  private int pwdState;

  // 密码所处的操作状态，1：添加；2：删除；3：更新；4：冻结；5：解冻结
  private int operation;

  // 当前操作所处的阶段，1：进行中，正在等待设备反馈；2：操作失败；3：操作成功
  @SerializedName("operation_stage")
  private int operationStage;

  // 密码当前有效期状态，1：有效期内；2：有效期外
  @SerializedName("permission_state")
  private int permissionState;

  // 操作出错原因描述
  private String description;

  private String name;

  @SerializedName("send_to")
  private String sendTo;

  private Permission permission;

  @Getter
  @Setter
  @ToString
  public static class Permission {

    private long begin;

    private long end;

    // 密码权限类型，1:永久密码；2:有时效密码
    private int status;
  }
}
