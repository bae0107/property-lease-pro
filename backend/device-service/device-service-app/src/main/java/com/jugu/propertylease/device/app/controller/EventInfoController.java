package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.device.app.event.EventService;
import com.jugu.propertylease.device.common.entity.dto.EventInfoDTO;
import com.jugu.propertylease.device.common.entity.request.EventInfoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/eventInfo")
@RequiredArgsConstructor
@Tag(name = "硬件事件通知", description = "硬件事件通知相关操作与查询")
public class EventInfoController {

  private final EventService eventService;

  @PostMapping("findBatchEventInfosByCondition")
  @Operation(summary = "事件通知信息查询", description = "batchSize为一次拿的数据条数，passNum为已经拿过的数据条数，"
      +
      "hasRead为已读或未读（分页分批次支持，数据按日期排序），应先调用count接口确认需分几次拿")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<EventInfoDTO>> findBatchEventInfosByCondition(
      @RequestBody RequestDataInfo<EventInfoRequest> eventInfoRequestRequestDataInfo) {
    return eventService.findBatchEventInfo(eventInfoRequestRequestDataInfo.getData());
  }

  @PostMapping("countInfoByCondition")
  @Operation(summary = "事件通知信息条数", description = "传1回未读条数，2回已读条数")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Integer> countInfoByCondition(
      @RequestBody RequestDataInfo<EventInfoRequest> eventInfoRequestRequestDataInfo) {
    return eventService.countSizeByCondition(eventInfoRequestRequestDataInfo.getData());
  }

  @PostMapping("readEventMsg")
  @Operation(summary = "事件通知信息已读", description = "把请求id的信息读取状态改为已读")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo readEventMsg(
      @RequestBody RequestDataInfo<Long> readRequest) {
    return eventService.readEventInfoMsg(readRequest.getData());
  }
}
