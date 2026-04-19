package com.jugu.propertylease.device.common.entity.dto;

import com.jugu.propertylease.common.info.ReturnInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Schema(description = "表类结算请求")
@Accessors(chain = true)
public class MeterSettlementDTO {

  @Schema(description = "电表主键Id（业务内部）")
  private long id;

  @Schema(description = "当前读数（第三方）")
  private double consumeAmount;

  @Schema(description = "读数时间（第三方提供）")
  private String consumeRecordTime;

  @Schema(description = "计费周期开始的读数时间（第三方提供）")
  private String periodConsumeStartTime;

  @Schema(description = "计费周期开始的读数（第三方提供）")
  private double periodConsumeAmount;

  @Schema(description = "状态信息，是否成功，错误信息等")
  private ReturnInfo returnInfo;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MeterSettlementDTO meterSettlementDTO = (MeterSettlementDTO) o;
    return Objects.equals(id, meterSettlementDTO.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
