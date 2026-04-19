package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.device.app.locker.LockService;
import com.jugu.propertylease.device.common.entity.dto.LockInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.request.LockPwdOpRequest;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/device/lock")
@Tag(name = "智能锁硬件设施管理", description = "智能锁相关操作管理")
public class LockController {

  private final LockService lockService;

  @PostMapping("addNewLock")
  @Operation(summary = "添加智能锁", description = "请求结构中包含userId, userName，dto中:deviceId, provideOp必填, boundRoomId选填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo addNewLock(
      @RequestBody RequestDataInfo<LockInfoDTO> lockRequest) {
    return lockService.addNewLock(lockRequest);
  }

  @PostMapping("adjustLockRoom")
  @Operation(summary = "智能锁房间换绑/解绑", description = "请求结构中包含userId, userName，dto中填写id、roomId（解绑填空值）")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo adjustLockBoundRoom(
      @RequestBody RequestDataInfo<LockInfoDTO> adjustRequest) {
    return lockService.adjustBoundRoom(adjustRequest);
  }

  @PostMapping("addLockPwd")
  @Operation(summary = "智能锁添加密码", description = "请求结构中所有信息为必填信息")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<AddLockPwdResponse> addLockPwd(
      @RequestBody RequestDataInfo<AddLockPwdRequest> addPwdRequest) {
    return lockService.addLockPwd(addPwdRequest.getData());
  }

  @PostMapping("findLockPwdSummary")
  @Operation(summary = "智能锁下所有密码状态查询", description = "请求结构中Long为锁的主键id")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<LockPwdsSummary> findLockPwdSummary(
      @RequestBody RequestDataInfo<Long> pwdSummaryRequest) {
    return lockService.findLockPwdSummary(pwdSummaryRequest.getData());
  }

  @PostMapping("frozenLockPwd")
  @Operation(summary = "智能锁密码冻结", description = "请求结构中2个参数都必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<ServiceRecordDTO> frozenLockPwd(
      @RequestBody RequestDataInfo<LockPwdOpRequest> frozenPwdRequest) {
    return lockService.frozenLockPwd(frozenPwdRequest.getData());
  }

  @PostMapping("unfrozenLockPwd")
  @Operation(summary = "智能锁密码解冻", description = "请求结构中2个参数都必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<ServiceRecordDTO> unfrozenLockPwd(
      @RequestBody RequestDataInfo<LockPwdOpRequest> unfrozenPwdRequest) {
    return lockService.unfrozenLockPwd(unfrozenPwdRequest.getData());
  }

  @PostMapping("delLockPwd")
  @Operation(summary = "智能锁密码删除", description = "请求结构中2个参数都必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<ServiceRecordDTO> delLockPwd(
      @RequestBody RequestDataInfo<LockPwdOpRequest> delPwdRequest) {
    return lockService.delLockPwd(delPwdRequest.getData());
  }

  @PostMapping("findLockInfoByRoomId")
  @Operation(summary = "按绑定房源ID查询智能锁信息列表", description = "roomId 字符串")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<LockInfoDTO>> findLockInfoByRoomId(
      @RequestBody RequestDataInfo<String> lockRequest) {
    return lockService.findLockInfoByRoomId(lockRequest.getData());
  }

  @PostMapping("findLockInfoByRoomIds")
  @Operation(summary = "按绑定房源IDs查询智能锁信息列表", description = "roomId 字符串链表")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<LockInfoDTO>> findLockInfoByRoomIds(
      @RequestBody RequestDataInfo<List<String>> lockRequest) {
    return lockService.findLockInfoByRoomIds(lockRequest.getData());
  }

  @PostMapping("findLockInfoByIds")
  @Operation(summary = "智能锁主键IDs查询智能锁信息列表", description = "id 智能锁主键LONG ids")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<LockInfoDTO>> findLockInfoByIds(
      @RequestBody RequestDataInfo<List<Long>> lockRequest) {
    return lockService.findLockInfoByIds(lockRequest.getData());
  }

  @PostMapping("findLockInfoById")
  @Operation(summary = "智能锁主键ID查询智能锁信息", description = "id 智能锁主键LONG")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<LockInfoDTO> findLockInfoById(
      @RequestBody RequestDataInfo<Long> lockRequest) {
    return lockService.findLockInfoById(lockRequest.getData());
  }

  @PostMapping("delLockForce")
  @Operation(summary = "无校验删除智能锁，有效值改为2（已删除）", description = "用户名、用户ID、以及需要删除的智能锁主键id必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo delLockForce(
      @RequestBody RequestDataInfo<Long> lockRequest) {
    return lockService.delLock(lockRequest);
  }
}
