package com.jugu.propertylease.device.app.meter.water;

import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter.HeYiWMeterCheckHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter.HeYiWMeterReadHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.water.YunDingWMeterCheckHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.water.YunDingWMeterRecordHandler;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;

/**
 * 第三方服务商请求水表收发管理
 */
public enum WaterRequestOp implements WMeterI {
  /**
   * 云丁不提供实时抄表接口，因此抄表依赖回调
   */
  YUN_DING {
    @Override
    public WMeterCheckResponse findWMeterInfoRequest(String deviceId) {
      YunDingWMeterCheckHandler handler = new YunDingWMeterCheckHandler(deviceId);
      return handler.submitRequest();
    }

    @Override
    public DeviceResponse sendWaterRecordRequest(String deviceId) {
      YunDingWMeterRecordHandler handler = new YunDingWMeterRecordHandler(deviceId);
      return handler.submitRequest();
    }

    @Override
    public ReturnDataInfo<ServiceRecordDTO> readWaterRecordRequest(String deviceId,
        WaterMeterProcessor meterProcessor,
        long id, String userName, String userId) {
      WaterMeterProcessor.WaterRecordTaskRunner recordTaskRunner = new WaterMeterProcessor.WaterRecordTaskRunner() {
        @Override
        public DeviceResponse submitAsyncRecord(String deviceId) {
          return sendWaterRecordRequest(deviceId);
        }
      };

      return meterProcessor.processWaterRecordAsync(recordTaskRunner, deviceId,
          ProviderOpE.YUN_DING.getIndex());
    }
  },

  /**
   * 合一为实时抄表，信息查询与抄表为findInfo和read两个接口
   */
  HE_YI {
    @Override
    public WMeterCheckResponse findWMeterInfoRequest(String deviceId) {
      HeYiWMeterCheckHandler handler = new HeYiWMeterCheckHandler(deviceId);
      return handler.submitRequest();
    }

    @Override
    public DeviceResponse sendWaterRecordRequest(String deviceId) {
      return null;
    }

    @Override
    public ReturnDataInfo<ServiceRecordDTO> readWaterRecordRequest(String deviceId,
        WaterMeterProcessor meterProcessor,
        long id, String userName, String userId) {
      WaterMeterProcessor.WaterRecordTaskRunner taskRunner = new WaterMeterProcessor.WaterRecordTaskRunner() {
        @Override
        public WMeterCheckResponse runRecordTask(String deviceId, long id) {
          HeYiWMeterReadHandler handler = new HeYiWMeterReadHandler(deviceId);
          return handler.submitRequest();
        }
      };

      return meterProcessor.processWaterRecordSync(taskRunner, id, deviceId, userName, userId,
          ProviderOpE.HE_YI.getIndex());
    }
  }
}
