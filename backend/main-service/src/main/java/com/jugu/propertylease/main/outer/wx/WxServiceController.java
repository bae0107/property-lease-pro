package com.jugu.propertylease.main.outer.wx;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/main/wxLogin")
@Tag(name = "微信服务接口", description = "微信服务功能管理")
public class WxServiceController {

  private final WxOpenServiceTools serviceTools;

  @GetMapping("wxLoginNotify")
  @Operation(summary = "微信登录回调通知", description = "微信登录回调通知")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  boolean wxLoginNotify(@RequestParam("code") String code, @RequestParam("state") String state,
      HttpSession session) {
    Optional<String> openIdOp = serviceTools.getUserOpenId(code, state, session);
    if (openIdOp.isPresent()) {
      log.info(openIdOp.get());
      return true;
    }
    return false;
  }
}
