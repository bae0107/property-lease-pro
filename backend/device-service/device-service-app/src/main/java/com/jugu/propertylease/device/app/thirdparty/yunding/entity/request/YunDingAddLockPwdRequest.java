package com.jugu.propertylease.device.app.thirdparty.yunding.entity.request;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingAddLockPwdRequest extends YunDingRequest {

  @SerializedName("phonenumber")
  private String phone;

  @SerializedName("is_default")
  private int isDefault;

  @SerializedName("password")
  private String password;

  @SerializedName("name")
  private String name;

  @SerializedName("permission_begin")
  private long begin;

  @SerializedName("permission_end")
  private long end;

  public YunDingAddLockPwdRequest(AddLockPwdRequest addLockPwdRequest, String token) {
    super(addLockPwdRequest.getDeviceId(), token);
    this.phone = addLockPwdRequest.getPhone();
    this.isDefault = 0;
    this.password = addLockPwdRequest.getPassword();
    this.name = addLockPwdRequest.getName();
    this.begin = addLockPwdRequest.getBegin();
    this.end = addLockPwdRequest.getEnd();
  }
}
