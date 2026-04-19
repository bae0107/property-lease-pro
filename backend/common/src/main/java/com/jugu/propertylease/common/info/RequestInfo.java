package com.jugu.propertylease.common.info;

import com.jugu.propertylease.common.utils.Common;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@Schema(description = "请求身份信息")
public class RequestInfo {

  @Schema(description = "用户ID")
  private String userId;

  @Schema(description = "用户名称")
  private String userName;

  public RequestInfo(String userId, String userName) {
    this.userId = userId;
    this.userName = userName;
  }

  public boolean isValid() {
    return !Common.isStringInValid(userId) && !Common.isStringInValid(userName);
  }
}
