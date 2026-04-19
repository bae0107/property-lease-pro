package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.propertymgr.AuthUtil;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionBaseInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionFilter;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
@RequestMapping("/main/region")
@Tag(name = "区域管理", description = "区域管理功能")
public class RegionController {

  private final AdminRegionService adminRegionService;

  @GetMapping("getAllRegionSettings")
  @Operation(summary = "获取区域设定参数列表", description = "获取所有中国区域参数，直辖市到3级行政区，其他到2级行政区")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<AdminRegions.Province>> getAllRegionSettings() {
    return adminRegionService.findAllRegionSettings();
  }

  @PostMapping("createRegion")
  @Operation(summary = "创建新区域", description = "创建新区域,用户名,用户id必填,request中除des外必填")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo createRegion(
      @RequestBody RequestDataInfo<CreateRegionRequest> createRegionRequestRequestDataInfo) {
    return adminRegionService.addNewRegion(createRegionRequestRequestDataInfo);
  }

  @GetMapping("findRegionById")
  @Operation(summary = "按区域ID查找区域信息", description = "按ID查找区域信息")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<RegionInfoDTO> findRegionById(@RequestParam("regionId") String regionId) {
    return adminRegionService.findRegionById(regionId);
  }

  @PostMapping("findRegionsByIds")
  @Operation(summary = "按区域IDs批量查找区域信息", description = "按IDs批量查找区域信息")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<RegionInfoDTO>> findRegionsByIds(@RequestBody Set<String> regionIds) {
    return adminRegionService.findRegionsByIds(regionIds);
  }

  @PostMapping("updateRegionDes")
  @Operation(summary = "更新区域描述", description = "按ID更新区域描述")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnInfo updateRegionDes(@RequestBody RegionBaseInfo regionBaseInfo) {
    String userName = AuthUtil.getUserName();
    String userId = AuthUtil.getUserId();
    if (Common.isStringInValid(userName) || Common.isStringInValid(userId)) {
      return ReturnInfo.failByType(ErrorType.AUTH_ERROR);
    }
    return adminRegionService.updateRegionDes(regionBaseInfo, userName, userId);
  }

  @PostMapping("findRegionsByFilter")
  @Operation(summary = "按过滤器查看所有区域", description = "过滤器字段为空时返回所有区域，过滤器内有字段时按条件过滤")
  @ApiResponse(responseCode = "200", description = "请求发送成功")
  ReturnDataInfo<List<RegionInfoDTO>> findRegionsByFilter(@RequestBody RegionFilter regionFilter) {
    return adminRegionService.findRegionsByFilter(regionFilter);
  }
}
