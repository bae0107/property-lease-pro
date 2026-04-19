package com.jugu.propertylease.device.app.controller;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.callback.CallBackTaskMgrE;
import com.jugu.propertylease.device.app.callback.CallbackTaskAllocator;
import com.jugu.propertylease.device.app.callback.CallbackUtil;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingEventDTO;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingEventMgr;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingEventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequestMapping("/device/out/notify")
@Tag(name = "第三方回调与通知管理", description = "第三方回调与通知管理接口")
@Log4j2
@RequiredArgsConstructor
public class ThirdPartyNotifyController {

  private static final String YUN_DING_NOTIFY = "http://111.com/callback";

  private static final String YUN_DING_EVENT_LISTENER = "http://111.com/event";

  private final CallbackTaskAllocator callbackTaskAllocator;

  private final YunDingEventMgr yunDingEventMgr;

  @PostMapping("yunDingNotifyCallBack")
  @Operation(summary = "云丁回调通知")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  void yunDingNotifyCallBack(
      @Parameter(description = "云丁回调通知结构", required = true) @RequestBody String paramsStr) {
    if (Common.isStringInValid(paramsStr)) {
      return;
    }
    log.info(paramsStr);
//        try {
    Map<String, String> paramsMap = CallbackUtil.convertToParamMap(paramsStr);
    log.info(paramsMap);
//            if (CallbackUtil.verifySign(paramsMap, YUN_DING_NOTIFY)) {
    CallBackTaskMgrE.YUN_DING.process(paramsMap, callbackTaskAllocator);
//            } else {
//                log.error("sign check failed maybe attack by others!");
//            }
//        } catch (NoSuchAlgorithmException e) {
//            log.error("algorithm fail when check third party sign!");
//        }

  }

  @PostMapping("yunDingEventListener")
  @Operation(summary = "云丁事件通知")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  void yunDingEventListener(
      @Parameter(description = "云丁事件通知结构", required = true) @RequestBody String paramsStr) {
    if (Common.isStringInValid(paramsStr)) {
      return;
    }
    log.info(paramsStr);
//        try {
    Map<String, String> paramsMap = CallbackUtil.convertToParamMap(paramsStr);
//            if (CallbackUtil.verifySign(paramsMap, YUN_DING_EVENT_LISTENER)) {
    YunDingEventDTO eventDTO = GsonFactory.fromJson(GsonFactory.toJson(paramsMap),
        YunDingEventDTO.class);
    String type = eventDTO.getEvent();
    if (!Common.isStringInValid(type)) {
      try {
        YunDingEventType eventType = YunDingEventType.valueOf(type.trim());
        eventType.run(eventDTO, yunDingEventMgr);
      } catch (IllegalArgumentException e) {
        log.error("yunDing event type not exist! type:{}", type);
      }
    }
//            } else {
//                log.error("sign check failed maybe attack by others!");
//            }
//        } catch (NoSuchAlgorithmException e) {
//            log.error("algorithm fail when check third party sign!");
//        }

  }
}
