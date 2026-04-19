package com.jugu.propertylease.billing.app.controller;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.result.Result;
import com.jugu.propertylease.main.client.api.AuthApiClient;
import com.jugu.propertylease.main.client.model.LoginResult;
import com.jugu.propertylease.main.client.model.PasswordLoginRequest;
import com.jugu.propertylease.main.client.model.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/billing/test")
@Tag(name = "测试", description = "测试相关操作")
public class TestController {

  private final AuthApiClient iamApiClient;

  @PostMapping("createTest1")
  @Operation(summary = "如何远程调用api1", description = "远程调用成功正常流程；失败自己不显示处理：失败也失败")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  LoginResult testCreate1() {
    // 由于会抛出businessException，所以会被重新作为500？抛出

    LoginResult loginResult = iamApiClient.passwordLogin(
        new PasswordLoginRequest("123", "12345678"));
    return loginResult;
  }

  @PostMapping("createTest2")
  @Operation(summary = "如何远程调用api2", description = "远程调用成功正常流程；失败自己处理异常 比如：仅处理某一种code类型降级、其他失败")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  LoginResult testCreate2() {
    try {
      return iamApiClient.passwordLogin(new PasswordLoginRequest("testname", "password"));
    } catch (BusinessException e) {

      if (e.getErrorCode().equals("IAM_TOKEN_MISSING")) {
        return new LoginResult("mocktoken", "mockFtoken", 1, 1L, UserType.TENANT);
      }
      throw e;
    } catch (Throwable throwable) {
      System.out.println(throwable.getMessage());
      throw throwable;
    }
  }

  @PostMapping("createTest3")
  @Operation(summary = "如何远程调用api3", description = "远程调用成功正常流程；失败自己显式处理失败，比如：失败降级，业务继续")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  LoginResult testCreate3() {
    return Result.of(
            () -> iamApiClient.passwordLogin(new PasswordLoginRequest("testname", "password")))
        .getOrElseGet(
            errorResponse -> new LoginResult("mocktoken", "mockFtoken", 1, 1L, UserType.TENANT));
  }
}
