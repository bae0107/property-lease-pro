package com.jugu.propertylease.main.propertymgr.store;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.propertymgr.AuthUtil;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreBaseInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreFilter;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreInfoDTO;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreStrategyInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("ClassCanBeRecord")
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/main/store")
@Tag(name = "门店管理", description = "门店管理功能")
public class StoreController {

  private final StoreService storeService;

  @PostMapping("createStore")
  @Operation(summary = "创建新门店", description = "创建新门店, StoreBaseInfo中名称，地址，描述，区域为必填项")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo createStore(@RequestBody StoreBaseInfo storeBaseInfo) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return storeService.createStore(storeBaseInfo, userName, userId);
  }

  @PostMapping("updateStoreBase")
  @Operation(summary = "更新门店基本信息", description = "更新门店基本信息，更改更新字段后，StoreBaseInfo整体发回")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo updateStoreBase(@RequestBody StoreBaseInfo storeBaseInfo) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return storeService.updateStoreBase(storeBaseInfo, userName, userId);
  }

  @GetMapping("findStoreById")
  @Operation(summary = "按门店ID查找门店信息", description = "按ID查找门店信息")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<StoreInfoDTO> findStoreById(@RequestParam("storeId") long storeId) {
    return storeService.findStoreById(storeId);
  }

  @PostMapping("updateStoreStrategy")
  @Operation(summary = "更新门店策略信息", description = "更新门店策略信息: 结构中关闭通用密码时密码可以为null，其余必须填写, id拼接在path")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo updateStoreStrategy(@RequestParam("storeId") long storeId,
      @RequestBody StoreStrategyInfo strategyInfo) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return storeService.updateStrategyInfo(strategyInfo, userName, userId, storeId);
  }

  @PostMapping("findStoresByFilter")
  @Operation(summary = "按过滤器查找门店信息", description = "可以按门店名称过滤查询门店信息，不填则返回全部门店")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<StoreInfoDTO>> findStoresByFilter(@RequestBody StoreFilter storeFilter) {
    return storeService.findStoresByFilter(storeFilter);
  }
}
