package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.common.utils.Common;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "区域创建请求，按后台提供的区域信息查询接口填写")
public class CreateRegionRequest {

  @Schema(description = "省级名称")
  private String province;

  @Schema(description = "省级编码")
  private String provinceCode;

  @Schema(description = "市级名称")
  private String city;

  @Schema(description = "市级编码")
  private String cityCode;

  @Schema(description = "区级名称")
  private String district;

  @Schema(description = "区级编码")
  private String districtCode;

  @Schema(description = "区域描述（选填）")
  private String regionDes;

  public boolean isLegal() {
    return !Common.isStringInValid(province) && !Common.isStringInValid(provinceCode)
        && !Common.isStringInValid(city)
        && !Common.isStringInValid(cityCode);
  }
}
