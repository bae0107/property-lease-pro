package com.jugu.propertylease.billing.app.controller;

import com.jugu.propertylease.billing.app.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequiredArgsConstructor
@RequestMapping("/billing/wx")
@Tag(name = "支付回调管理", description = "第三方支付回调接口")
public class BillingWxNotifyController {

  private final BillingService billingService;

  @PostMapping("payNotify")
  @Operation(summary = "微信支付回调", description = "微信支付的回调结果")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  String processWxPayNotify(HttpServletRequest request) {
    return billingService.processWxPayNotify(request);
  }
}
