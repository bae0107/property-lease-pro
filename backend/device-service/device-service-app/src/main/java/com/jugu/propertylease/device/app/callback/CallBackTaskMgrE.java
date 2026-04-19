package com.jugu.propertylease.device.app.callback;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.enums.ProviderOpE;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ThirdPartyServiceTemp;
import com.jugu.propertylease.device.app.meter.MeterSwitchStatus;
import com.jugu.propertylease.device.app.meter.MeterUtil;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingCallbackDTO;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingCallbackOpTypeE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.notify.YunDingAddPwdNotify;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.notify.YunDingMeterNotify;
import com.jugu.propertylease.device.app.thirdparty.yunding.error.YunDingErrorMsgInfo;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public enum CallBackTaskMgrE implements CallBackTaskI {
  YUN_DING {
    @Override
    public void process(Map<String, String> paramMap, CallbackTaskAllocator taskMgr) {
      YunDingCallbackDTO callbackDTO = GsonFactory.fromJson(GsonFactory.toJson(paramMap),
          YunDingCallbackDTO.class);
      log.info("callbackDto: {}", callbackDTO);
      String serviceId = callbackDTO.getServiceId();
      if (Common.isStringInValid(serviceId)) {
        log.error("third party call back failed due to service null or empty!");
        return;
      }
      String result = callbackDTO.getResult();
      if (Common.isStringInValid(result)) {
        log.error("third party call back failed due to result empty!");
        return;
      }
      ProviderOpE providerOpE = ProviderOpE.YUN_DING;
      CallBackTaskCallbackRunnerI backTaskCallbackRunnerI = new CallBackTaskCallbackRunnerI() {
        @Override
        public void callbackMeterSwitch(int recordOpType, long id,
            ElectronicMeterService electronicMeterService) {
          YunDingMeterNotify switchNotify = GsonFactory.fromJson(result, YunDingMeterNotify.class);
          int errorNo = switchNotify.getErrorNo();
          int opType = switchNotify.getOpType();
          if (errorNo != YunDingErrorMsgInfo.SUCCESS2.getErrCode()) {
            log.error("third party callback fail: name: {}, code: {}, op: {}",
                providerOpE.getName(), errorNo, callbackDTO.getService());
            taskMgr.processFailCallBackTask(serviceId, providerOpE.getIndex(),
                ThirdPartyServiceRecordMgr.ServiceResult.FAIL,
                CallBackTaskMgrE.genErrorInfo(errorNo));
            return;
          }
          EMeterSwitchResponse.SwitchTypeE switchType =
              opType == YunDingCallbackOpTypeE.CLOSE_SWITCH.getIndex()
                  ? EMeterSwitchResponse.SwitchTypeE.CLOSE : EMeterSwitchResponse.SwitchTypeE.OPEN;
          MeterSwitchStatus middleStatus = MeterUtil.findMiddleStatusBySwitchType(switchType);
          int middleIndex = middleStatus.getIndex();
          int switchTypeIndex = switchType.getSwitchType();
          if (switchTypeIndex == recordOpType) {
            String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
            Optional<Integer> taskResultOp = electronicMeterService.getSwitchProcessor()
                .parseAsyncEMeterSwitchTaskResult(serviceId, providerOpE.getIndex(), id,
                    middleIndex,
                    switchTypeIndex, time, providerOpE.getName(), "serviceId:" + serviceId);
            if (taskResultOp.isEmpty()) {
              log.error(
                  "callback: fail to adjust meter due to serious problem: records is missing, meter info null or service temp null!");
              return;
            }
            int taskResult = taskResultOp.get();
            if (taskResult == middleStatus.getIndex()) {
              log.warn(
                  "callback: adjust meter not updated due to required middle status and record different!");
              return;
            }
            return;
          }
          log.error("third party callback fail due to incompatible op type, require: {}, find: {}",
              recordOpType, switchTypeIndex);
        }

        @Override
        public void callbackEMeterRead(int recordOpType, long id,
            ElectronicMeterService electronicMeterService) {

        }

        @Override
        public void callbackWMeterRead(long id, WaterMeterService waterMeterService) {
          YunDingMeterNotify recordNotify = GsonFactory.fromJson(result, YunDingMeterNotify.class);
          String recordTime = Common.findTimeByMillSecondTimestamp(callbackDTO.getTime());
          int errorNo = recordNotify.getErrorNo();
          if (errorNo != YunDingErrorMsgInfo.SUCCESS2.getErrCode()) {
            log.error("third party callback fail: name: {}, code: {}, op: {}",
                providerOpE.getName(), errorNo, callbackDTO.getService());
            taskMgr.processFailCallBackTask(serviceId, providerOpE.getIndex(),
                ThirdPartyServiceRecordMgr.ServiceResult.FAIL,
                CallBackTaskMgrE.genErrorInfo(errorNo));
            return;
          }
          double amount = recordNotify.getAmount();
          if (!waterMeterService.getWaterMeterProcessor()
              .syncWMeterRecordTask(serviceId, providerOpE.getIndex(),
                  id, amount, recordTime, providerOpE.getName(), "serviceId:" + serviceId)) {
            log.error("third party callback fail: due to db data missing: serviceId: {}, id: {}",
                serviceId, id);
          }
        }

        @Override
        public void callBackLockPwdOp(ThirdPartyServiceTemp serviceTemp,
            ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr) {
          YunDingAddPwdNotify notify = GsonFactory.fromJson(result, YunDingAddPwdNotify.class);
          int errorNo = notify.getErrorNo();
          if (errorNo != YunDingErrorMsgInfo.SUCCESS2.getErrCode()) {
            log.error("third party callback fail: name: {}, code: {}, op: {}",
                providerOpE.getName(), errorNo, callbackDTO.getService());
            taskMgr.processFailCallBackTask(serviceId, providerOpE.getIndex(),
                ThirdPartyServiceRecordMgr.ServiceResult.FAIL,
                CallBackTaskMgrE.genErrorInfo(errorNo));
            return;
          }
          String pwdId = String.valueOf(notify.getPwdId());
          String recordId = serviceTemp.getAdditionalinfo();
          if (pwdId.equals(recordId)) {
            thirdPartyServiceRecordMgr.parseServiceResult(serviceTemp,
                ThirdPartyServiceRecordMgr.ServiceResult.SUCCESS, "");
            return;
          }
          log.error("third party call back failed due to addInfo not match, id: {}, recordId: {}!",
              pwdId, recordId);
        }
      };
      taskMgr.processCallbackTask(providerOpE, serviceId, backTaskCallbackRunnerI);
    }
  },

  HE_YI {
    @Override
    public void process(Map<String, String> paramMap, CallbackTaskAllocator taskMgr) {

    }
  };

  private static String genErrorInfo(int errorNo) {
    String errMsg = YunDingErrorMsgInfo.findMsgInfo(errorNo).getErrMsg();
    return errorNo + ":" + errMsg;
  }
}
