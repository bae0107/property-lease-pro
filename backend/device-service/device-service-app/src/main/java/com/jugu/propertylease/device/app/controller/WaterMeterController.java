package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.common.entity.dto.MeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.dto.WMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.request.MeterBatchNotifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RequiredArgsConstructor
@RestController
@RequestMapping("/device/water")
@Tag(name = "水表硬件设施管理", description = "水表相关操作管理")
public class WaterMeterController {

  private final WaterMeterService waterMeterService;

  /**
   * 按数据库当前数据返回（需要实时的话，先调用单独的抄表接口发起抄表后再调用服务确认接口完成后再调用此接口结算）
   */
  @PostMapping("settleWaterMeter")
  @Operation(summary = "单个水表的结算抄表发起接口", description = "请求结构中包含userId, userName，Long为水表id")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<MeterSettlementDTO> settleWaterMeter(
      @RequestBody RequestDataInfo<Long> wMeterRequest) {
    return waterMeterService.settleWaterMeter(wMeterRequest.getData());
  }

  /**
   * 先调用settleWaterMeter发起
   */
  @PostMapping("settleWaterSuccessNotify")
  @Operation(summary = "单个水表的结算完成后回调接口", description = "请求结构中包含userId, userName，MeterSettlementDTO为发起接口的返回值")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo settleWaterSuccessNotify(
      @RequestBody RequestDataInfo<MeterSettlementDTO> wMeterSettlementRequest) {
    return waterMeterService.settleWSuccessNotify(wMeterSettlementRequest);
  }

  /**
   * 为数据库当前表值，不会发起第三方同步
   */
  @PostMapping("settleWaterMeterBatch")
  @Operation(summary = "水表的批量结算抄表发起接口", description = "请求结构中包含userId, userName，Set<Long>为水表ids")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Map<Boolean, Set<MeterSettlementDTO>>> settleWaterMeterBatch(
      @RequestBody RequestDataInfo<Set<Long>> settleBatchRequest) {
    return waterMeterService.settleWaterMeterBatch(settleBatchRequest.getData());
  }

  @PostMapping("adjustWMeterRoom")
  @Operation(summary = "水表房间换绑/解绑", description = "请求结构中包含userId, userName，dto中填写id、roomId（解绑填空值）")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo adjustWMeterBoundRoom(
      @RequestBody RequestDataInfo<MeterInfoDTO> adjustRequest) {
    return waterMeterService.adjustBoundRoom(adjustRequest);
  }

  /**
   * 先调用settleWaterMeterBatch发起
   */
  @PostMapping("settleWaterSuccessNotifyBatch")
  @Operation(summary = "批量水表的结算完成后回调接口", description = "请求结构中包含userId, userName，Set<MeterSettlementDTO>为发起接口的返回值")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Map<Boolean, Map<Long, ReturnInfo>>> settleWaterSuccessNotifyBatch(
      @RequestBody MeterBatchNotifyRequest notifyRequest) {
    return waterMeterService.settleSuccessWaterNotifyBatch(notifyRequest);
  }

  @PostMapping("addNewWMeter")
  @Operation(summary = "更换新水表时调用", description = "新增水表请求数据,userId, userName, installType, meterNo, deviceModelId, deviceId, providerOp必填，boundRoomId选填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo addNewWMeter(
      @RequestBody RequestDataInfo<WMeterInfoDTO> wMeterRequest) {
    return waterMeterService.addNewWaterMeter(wMeterRequest);
  }

  @PostMapping("recordWMeter")
  @Operation(summary = "发起水表抄表", description = "水表主键id, 返回异步任务服务信息，使用该信息调用异步任务确认接口，确认服务状态")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<ServiceRecordDTO> recordWMeter(
      @RequestBody RequestDataInfo<Long> wMeterRequest) {
    return waterMeterService.sendWMeterRecordRequest(wMeterRequest);
  }

  @PostMapping("findWMeterByRoomId")
  @Operation(summary = "按绑定房源ID查询水表设备信息列表", description = "roomId 字符串")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<WMeterInfoDTO>> findWMeterByRoomId(
      @RequestBody RequestDataInfo<String> wMeterRequest) {
    return waterMeterService.findWMeterInfoByRoomId(wMeterRequest.getData());
  }

  @PostMapping("findWMeterByRoomIds")
  @Operation(summary = "按绑定房源IDs查询水表设备信息列表", description = "roomId 字符串链表")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<WMeterInfoDTO>> findWMeterByRoomIds(
      @RequestBody RequestDataInfo<List<String>> wMeterRequest) {
    return waterMeterService.findWMeterInfoByRoomIds(wMeterRequest.getData());
  }

  @PostMapping("findWMeterByIds")
  @Operation(summary = "水表主键IDs查询水表设备信息列表", description = "id 水表主键LONG ids")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<WMeterInfoDTO>> findWMeterByIds(
      @RequestBody RequestDataInfo<List<Long>> wMeterRequest) {
    return waterMeterService.findWMeterInfoByIds(wMeterRequest.getData());
  }

  @PostMapping("findWMeterById")
  @Operation(summary = "水表主键ID查询水表设备信息", description = "id 水表主键LONG")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<WMeterInfoDTO> findWMeterById(
      @RequestBody RequestDataInfo<Long> wMeterRequest) {
    return waterMeterService.findWMeterInfoById(wMeterRequest.getData());
  }

  @PostMapping("delWMeterForce")
  @Operation(summary = "无校验删除水表，有效值改为2（已删除）", description = "用户名、用户ID、以及需要删除的水表主键id必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo delWMeterForce(
      @RequestBody RequestDataInfo<Long> wMeterRequest) {
    return waterMeterService.delWMeter(wMeterRequest);
  }
}
