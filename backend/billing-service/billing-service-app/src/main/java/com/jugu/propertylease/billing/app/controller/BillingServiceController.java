package com.jugu.propertylease.billing.app.controller;

import com.jugu.propertylease.billing.app.service.BillingService;
import com.jugu.propertylease.billing.common.entity.BillingRecordDTO;
import com.jugu.propertylease.billing.common.entity.BillingRequest;
import com.jugu.propertylease.billing.common.entity.BillingStatus;
import com.jugu.propertylease.billing.common.entity.wx.WxPayRequest;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequiredArgsConstructor
@RequestMapping("/billing/service")
@Tag(name = "账单管理服务", description = "账单管理服务相关操作")
public class BillingServiceController {

  private final BillingService billingService;

  @PostMapping("createBill")
  @Operation(summary = "创建账单", description = "创建支付所需账单，请求所有字段必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<String> createBill(
      @RequestBody RequestDataInfo<BillingRequest> billingRequestRequestDataInfo) {
    return billingService.createBill(billingRequestRequestDataInfo);
  }

  @PostMapping("cancelBill")
  @Operation(summary = "取消订单", description = "用户取消或轮询超时后使用")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo cancelBill(
      @RequestBody RequestDataInfo<String> cancelRequest) {
    return billingService.cancelBill(cancelRequest.getData());
  }

  @PostMapping("payByWx")
  @Operation(summary = "调起微信支付", description = "发起微信支付时传入支付类型：H5/QR/APP,返回的response中按类型获取结果"
      +
      ",订单超时支付的话，为TIME_OUT错误，需读取错误MSG")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<WxPayResponse> payByWx(
      @RequestBody RequestDataInfo<WxPayRequest> wxPayRequestRequestDataInfo) {
    return billingService.processWxPay(wxPayRequestRequestDataInfo.getData());
  }

  @PostMapping("checkBillStatus")
  @Operation(summary = "查询订单内部状态", description = "发起微信支付时传入支付类型：H5/QR/APP,返回的response中按类型获取结果")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<BillingStatus> checkBillStatus(
      @RequestBody RequestDataInfo<String> statusRequest) {
    return billingService.checkInternalBillingStatus(statusRequest.getData());
  }

  @PostMapping("findBillById")
  @Operation(summary = "按账单ID查询账单", description = "按账单ID查询账单,账单ID无效或不存在返回input error")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<BillingRecordDTO> findBillById(
      @RequestBody RequestDataInfo<String> billRequest) {
    return billingService.findBillById(billRequest.getData());
  }

  @PostMapping("findBillsByIds")
  @Operation(summary = "按账单ID查询账单(批量)", description = "按账单ID查询账单(批量),账单IDs无效或不存在返回input error")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<BillingRecordDTO>> findBillsByIds(
      @RequestBody RequestDataInfo<List<String>> billRequest) {
    return billingService.findBillByIds(billRequest.getData());
  }

  @PostMapping("findBillsByStatus")
  @Operation(summary = "按账单状态查询账单", description = "按账单状态查询账单,无该状态订单时返回空列表")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<BillingRecordDTO>> findBillsByStatus(
      @RequestBody RequestDataInfo<BillingStatus> billRequest) {
    return billingService.findBillByStatus(billRequest.getData());
  }
}
