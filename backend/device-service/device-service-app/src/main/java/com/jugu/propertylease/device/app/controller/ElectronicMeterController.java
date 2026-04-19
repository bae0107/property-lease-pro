package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.common.entity.dto.EMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import com.jugu.propertylease.device.common.entity.request.MeterBatchNotifyRequest;
import com.jugu.propertylease.device.common.entity.request.MeterRequest;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequestMapping("/device/electricity")
@Tag(name = "电表硬件设施管理", description = "电表相关操作管理")
public class ElectronicMeterController {

  private final ElectronicMeterService electronicMeterService;

  public ElectronicMeterController(ElectronicMeterService electronicMeterService) {
    this.electronicMeterService = electronicMeterService;
  }

  //    @GetMapping("read/{id}")
//    @Operation(summary = "根据电表ID获取读数并刷新数据库相关字段")
//    @ApiResponse(responseCode = "200", description = "请求发送成功")
//    ReturnDataInfo<MeterCheckResponse> readAndSyncElectronicMeter(
//            @Parameter(description = "电表ID", required = true, example = "M001")
//            @PathVariable("id") long id) {
//        return electronicMeterService.readAndSyncElectronicMeter(id);
//    }
  @PostMapping("settleElectronicMeter")
  @Operation(summary = "单个电表的结算抄表发起接口", description = "请求结构中包含userId, userName，Long为电表id")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<MeterSettlementDTO> settleElectronicMeter(
      @RequestBody RequestDataInfo<Long> eleMeterRequest) {
    return electronicMeterService.settleElectronicMeter(eleMeterRequest);
  }

  @PostMapping("adjustEMeterRoom")
  @Operation(summary = "电表房间换绑/解绑", description = "请求结构中包含userId, userName，dto中填写id、roomId（解绑填空值）")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo adjustEMeterBoundRoom(
      @RequestBody RequestDataInfo<MeterInfoDTO> adjustRequest) {
    return electronicMeterService.adjustBoundRoom(adjustRequest);
  }

  @PostMapping("settleSuccessNotify")
  @Operation(summary = "单个电表的结算完成后回调接口", description = "请求结构中包含userId, userName，MeterSettlementDTO为发起接口的返回值")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo settleSuccessNotify(
      @RequestBody RequestDataInfo<MeterSettlementDTO> eMeterSettlementRequest) {
    return electronicMeterService.settleSuccessNotify(eMeterSettlementRequest);
  }

  @PostMapping("settleElectronicMeterBatch")
  @Operation(summary = "电表的批量结算抄表发起接口", description = "请求结构中包含userId, userName，Set<Long>为电表ids")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Map<Boolean, Set<MeterSettlementDTO>>> settleElectronicMeterBatch(
      @RequestBody RequestDataInfo<Set<Long>> settleBatchRequest) {
    return electronicMeterService.settleElectronicMeterBatch(settleBatchRequest);
  }

  @PostMapping("settleSuccessNotifyBatch")
  @Operation(summary = "批量电表的结算完成后回调接口", description = "请求结构中包含userId, userName，Set<MeterSettlementDTO>为发起接口的返回值")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Map<Boolean, Map<Long, ReturnInfo>>> settleSuccessNotifyBatch(
      @RequestBody MeterBatchNotifyRequest notifyRequest) {
    return electronicMeterService.settleSuccessNotifyBatch(notifyRequest);
  }

  @PostMapping("readAndSyncElectronicMeter")
  @Operation(summary = "根据电表ID获取读数并刷新数据库相关字段,会进行第三方接口调用", description = "新增电表请求数据,userId, userName, Long为电表ID必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<EMeterCheckResponse> readAndSyncElectronicMeter(
      @RequestBody RequestDataInfo<Long> eleMeterRequest) {
    return electronicMeterService.readAndSyncElectronicMeter(eleMeterRequest);
  }

  @PostMapping("addNewEMeter")
  @Operation(summary = "更换新电表时调用", description = "新增电表请求数据,userId, userName, installType, meterNo, deviceModelId, deviceId, providerOp必填，boundRoomId选填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo addNewEMeter(
      @RequestBody RequestDataInfo<EMeterInfoDTO> eleMeterRequest) {
    return electronicMeterService.addNewElectronicMeter(eleMeterRequest);
  }

  @PostMapping("switchEMeter")
  @Operation(summary = "电表开关闸操作，详细返回见方法内部", description = "userId, userName, id（业务内主键）, provideOp, serviceType必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Integer> switchEMeter(
      @RequestBody RequestDataInfo<MeterRequest> eleMeterRequest) {
    return electronicMeterService.adjustESwitch(eleMeterRequest);
  }

  @PostMapping("findEMeterByRoomId")
  @Operation(summary = "按绑定房源ID查询电表设备信息列表", description = "roomId 字符串")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<EMeterInfoDTO>> findEMeterByRoomId(
      @RequestBody RequestDataInfo<String> eleMeterRequest) {
    return electronicMeterService.findEMeterInfoByRoomId(eleMeterRequest.getData());
  }

  @PostMapping("findEMeterByRoomIds")
  @Operation(summary = "按绑定房源IDs查询电表设备信息列表", description = "roomId 字符串链表")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<EMeterInfoDTO>> findEMeterByRoomIds(
      @RequestBody RequestDataInfo<List<String>> eleMeterRequest) {
    return electronicMeterService.findEMeterInfoByRoomIds(eleMeterRequest.getData());
  }

  @PostMapping("findEMeterByIds")
  @Operation(summary = "电表主键IDs查询电表设备信息列表", description = "id 电表主键LONG ids")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<EMeterInfoDTO>> findEMeterByIds(
      @RequestBody RequestDataInfo<List<Long>> eleMeterRequest) {
    return electronicMeterService.findEMeterInfoByIds(eleMeterRequest.getData());
  }

  @PostMapping("findEMeterById")
  @Operation(summary = "电表主键ID查询电表设备信息", description = "id 电表主键LONG")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<EMeterInfoDTO> findEMeterById(
      @RequestBody RequestDataInfo<Long> eleMeterRequest) {
    return electronicMeterService.findEMeterInfoById(eleMeterRequest.getData());
  }

  @PostMapping("readEMeterPeriod")
  @Operation(summary = "获取电表一段时间内的用电量", description = "用户名、用户ID、以及结构内主键ID与起止时间必填（对于不支持该接口的电表，返回-1：不具备该功能）")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<EMeterPeriodResponse> readEMeterPeriod(
      @RequestBody RequestDataInfo<MeterRequest> eleMeterRequest) {
    return electronicMeterService.findElectronicUsagePeriod(eleMeterRequest.getData());
  }

  @PostMapping("delEMeterForce")
  @Operation(summary = "无校验删除电表，有效值改为2（已删除）", description = "用户名、用户ID、以及需要删除的电表主键id必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo delEMeterForce(
      @RequestBody RequestDataInfo<Long> eleMeterRequest) {
    return electronicMeterService.delEMeter(eleMeterRequest);
  }
}
