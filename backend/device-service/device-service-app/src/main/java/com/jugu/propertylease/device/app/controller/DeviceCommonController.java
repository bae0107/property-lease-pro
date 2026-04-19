package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/device/common")
@Tag(name = "硬件公共管理", description = "通用操作管理")
public class DeviceCommonController {

  private final ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  @PostMapping("checkAsyncService")
  @Operation(summary = "异步服务结果查询", description = "结果：0进行中、1成功、2失败")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<Integer> checkAsyncService(
      @RequestBody RequestDataInfo<ServiceRecordDTO> requestDataInfo) {
    ServiceRecordDTO recordDTO = requestDataInfo.getData();
    if (recordDTO == null) {
      return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
    }
    long serviceKey = recordDTO.getServiceKey();
    try {
      Optional<Integer> resOp = thirdPartyServiceRecordMgr.checkResult(serviceKey);
      if (resOp.isEmpty()) {
        return ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR, "serviceKey无效");
      }
      return ReturnDataInfo.successData(resOp.get());
    } catch (Exception e) {
      log.error("fail to send check async service due to db error: {}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }
}
