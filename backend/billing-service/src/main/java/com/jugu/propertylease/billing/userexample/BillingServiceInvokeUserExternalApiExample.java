package com.jugu.propertylease.billing.userexample;

import com.jugu.propertylease.main.client.api.IamApi;
import com.jugu.propertylease.main.client.model.RoleResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 这个类演示了如何在 Billing Service 中调用 User Service 的外部 API。 通过 Feign 客户端（IamApi）来获取用户的角色信息，并在日志中输出权限列表。
 * <p>
 * 注意：外部接口当前应该是系统外的web使用js调用，内部正常使用的应该是internal的api，不过方式是一样的
 */


@Service
@Slf4j
public class BillingServiceInvokeUserExternalApiExample {

  private final IamApi iamApi;

  public BillingServiceInvokeUserExternalApiExample(IamApi iamApi) {
    this.iamApi = iamApi;
  }


  ResponseEntity<RoleResponse> getUserDetails(Long userId) {
    ResponseEntity<RoleResponse> response = iamApi.iamRoleGet(userId.intValue());
    log.info("{}", Objects.requireNonNull(response.getBody()).getPermissions());
    return response;  // 调用 Feign 客户端接口方法
  }

  @PostConstruct
  public void test() {

    try {
      ResponseEntity<RoleResponse> response = iamApi.iamRoleGet(123);
      HttpStatusCode statusCode = response.getStatusCode();
      log.info(statusCode.toString());
      if (response.getBody() != null) {
        for (String permission : response.getBody().getPermissions()) {
          log.info("Permission: {}", permission);
        }
      }
      System.out.println(
          "peiyong ====" + Objects.requireNonNull(getUserDetails(123L).getBody()).getDescription());
    } catch (Exception e) {
      log.error("e: ", e);
    }
  }
}
