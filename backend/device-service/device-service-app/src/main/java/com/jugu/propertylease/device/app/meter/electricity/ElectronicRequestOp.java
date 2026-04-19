package com.jugu.propertylease.device.app.meter.electricity;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter.HeYiEMeterCheckHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter.HeYiEMeterReadHandler;
import com.jugu.propertylease.device.app.thirdparty.heyi.handler.meter.HeYiEMeterSwitchHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity.YunDingEMeterSwitchStateHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity.YunDingPeriodUsageHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity.YunDingReadEConsumeHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity.YunDingReadFullInfoHandler;
import com.jugu.propertylease.device.app.thirdparty.yunding.handler.electricity.YunDingSwitchEMeterHandler;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;
import lombok.extern.log4j.Log4j2;

/**
 * 第三方服务商请求电表收发管理
 */
@Log4j2
public enum ElectronicRequestOp implements EMeterI {
  YUN_DING {
    @Override
    public EMeterSwitchResponse switchMeterRequest(String deviceId,
        EMeterSwitchResponse.SwitchTypeE switchTypeE) {
      YunDingSwitchEMeterHandler handler = new YunDingSwitchEMeterHandler(deviceId, switchTypeE);
      return handler.submitRequest();
    }

    @Override
    public EMeterCheckResponse readMeterRequest(String deviceId) {
      YunDingReadEConsumeHandler handler = new YunDingReadEConsumeHandler(deviceId);
      return handler.submitRequest();
    }

    @Override
    public EMeterPeriodResponse findUsagePeriod(String deviceId, long startTime, long endTime) {
      YunDingPeriodUsageHandler handler = new YunDingPeriodUsageHandler(deviceId, startTime,
          endTime);
      return handler.submitRequest();
    }

    @Override
    public ReturnDataInfo<Integer> adjustSwitch(EMeterSwitchResponse.SwitchTypeE switchTypeE,
        ElectronicSwitchProcessor switchProcessor,
        String userName, String userId, ElectronicMeterInfo meterInfo) {
      ElectronicSwitchProcessor.SwitchTaskRunner switchTaskRunner = new ElectronicSwitchProcessor.SwitchTaskRunner() {
        @Override
        public EMeterSwitchResponse submitAsyncTask(String deviceId) {
          return switchMeterRequest(deviceId, switchTypeE);
        }

        @Override
        public EMeterCheckResponse runSyncStateTask(String deviceId) {
          YunDingEMeterSwitchStateHandler handler = new YunDingEMeterSwitchStateHandler(deviceId);
          return handler.submitRequest();
        }
      };
      return switchProcessor.runAsyncEMeterSwitchTask(switchTaskRunner, switchTypeE, userName,
          userId, meterInfo);
    }

    @Override
    public EMeterCheckResponse checkMeterFullState(String deviceId) {
      YunDingReadFullInfoHandler handler = new YunDingReadFullInfoHandler(deviceId);
      return handler.submitRequest();
    }
  },

  HE_YI {
    @Override
    public EMeterSwitchResponse switchMeterRequest(String deviceId,
        EMeterSwitchResponse.SwitchTypeE switchTypeE) {
      return null;
    }

    @Override
    public EMeterCheckResponse readMeterRequest(String deviceId) {
      HeYiEMeterReadHandler handler = new HeYiEMeterReadHandler(deviceId);
      return handler.submitRequest();
    }

    @Override
    public EMeterPeriodResponse findUsagePeriod(String deviceId, long startTime, long endTime) {
      EMeterPeriodResponse periodResponse = new EMeterPeriodResponse();
      periodResponse.failResponse(new ErrorInfo(-1, "合一电表不具备该功能"));
      return periodResponse;
    }

    @Override
    public ReturnDataInfo<Integer> adjustSwitch(EMeterSwitchResponse.SwitchTypeE switchTypeE,
        ElectronicSwitchProcessor switchProcessor,
        String userName, String userId, ElectronicMeterInfo meterInfo) {
      ElectronicSwitchProcessor.SwitchTaskRunner taskRunner = new ElectronicSwitchProcessor.SwitchTaskRunner() {
        @Override
        public EMeterSwitchResponse submitAsyncTask(String deviceId) {
          return null;
        }

        @Override
        public EMeterCheckResponse runSyncStateTask(String deviceId) {
          HeYiEMeterSwitchHandler handler = new HeYiEMeterSwitchHandler(deviceId, switchTypeE);
          return handler.submitRequest();
        }
      };
      return switchProcessor.runSyncEMeterSwitchTask(taskRunner, userName, userId, meterInfo,
          switchTypeE);
    }

    @Override
    public EMeterCheckResponse checkMeterFullState(String deviceId) {
      HeYiEMeterCheckHandler handler = new HeYiEMeterCheckHandler(deviceId);
      return handler.submitRequest();
    }
  }
}
