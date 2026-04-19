package com.jugu.propertylease.device.common.entity.request;

import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "批量抄表成功通知接口")
@NoArgsConstructor
public class MeterBatchNotifyRequest extends RequestDataInfo<Set<MeterSettlementDTO>> {

  public MeterBatchNotifyRequest(String userId, String userName, Set<MeterSettlementDTO> data) {
    super(userId, userName, data);
  }
}
