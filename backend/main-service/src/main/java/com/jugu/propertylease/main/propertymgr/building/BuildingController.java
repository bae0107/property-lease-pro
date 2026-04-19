package com.jugu.propertylease.main.propertymgr.building;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.propertymgr.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
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
@RequestMapping("/main/building")
@Tag(name = "楼栋管理", description = "楼栋管理功能")
public class BuildingController {

  private final BuildingService buildingService;

  @PostMapping("createBuilding")
  @Operation(summary = "创建新楼栋", description = "baseInfo, 绑定门店ID，楼栋名称，必填项")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo createBuilding(@RequestBody BuildingBaseInfo baseInfo) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return buildingService.createNewBuilding(baseInfo, userName, userId);
  }

  @PostMapping("updateFloors")
  @Operation(summary = "更新楼层信息", description = "set<Integer>中为涉及楼层, id拼接在path")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo updateStoreStrategy(@RequestParam("buildingId") String buildingId,
      @RequestBody Set<Integer> floors) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return buildingService.updateFloorsInfo(floors, buildingId, userName, userId);
  }

  @GetMapping("findBuildingById")
  @Operation(summary = "按楼栋ID查找楼栋信息", description = "按楼栋ID查找楼栋信息")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<BuildingDTO> findBuildingById(@RequestParam("buildingId") String buildingId) {
    return buildingService.findBuildingById(buildingId);
  }
}
