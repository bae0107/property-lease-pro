package com.jugu.propertylease.billing.app.wxpay;

import com.jugu.propertylease.billing.app.jooq.tables.pojos.BillingRecordInfo;
import com.jugu.propertylease.billing.app.repository.BillingRecordInfoRepository;
import com.jugu.propertylease.billing.app.service.BillingStatusTask;
import com.jugu.propertylease.billing.common.entity.BillingStatus;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.utils.Common;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WxBillingStatusTask extends BillingStatusTask {

  public static final String NORMAL_CANCEL = "订单支付超时，已自动取消";

  public static final String ABNORMAL_CANCEL = "订单支付超时，订单取消失败，请联系服务人员";

  private final String billingId;

  private final WxPayServiceMgr wxPayServiceMgr;

  private final boolean isHost;

  private final BillingRecordInfoRepository billingRecordInfoRepository;

  public WxBillingStatusTask(String billingId, WxPayServiceMgr wxPayServiceMgr, boolean isHost,
      BillingRecordInfoRepository billingRecordInfoRepository) {
    this.billingId = billingId;
    this.wxPayServiceMgr = wxPayServiceMgr;
    this.isHost = isHost;
    this.billingRecordInfoRepository = billingRecordInfoRepository;
  }

  @Override
  public boolean run() {
    log.info("running wx billing sync task id:{}", billingId);
    ReturnDataInfo<WxPayResponse.WxPayStatus> res = wxPayServiceMgr.checkPayStatus(billingId,
        isHost);
    if (res.isSuccess()) {
      try {
        BillingRecordInfo recordInfo = billingRecordInfoRepository.findBillForUpdate(billingId);
        if (recordInfo == null) {
          log.error(
              "run wx pay status task fail due to wx billing notify billing id not match in db, id: {}",
              billingId);
          return true;
        }
        String curStatus = recordInfo.getBillingstatus();
        if (!BillingStatus.PENDING.name().equals(curStatus)) {
          log.warn("bill sync task cancel due to task:{} status not pending, status:{}", billingId,
              curStatus);
          return true;
        }
        WxPayResponse.WxPayStatus wxPayStatus = res.getResponseData();
        switch (wxPayStatus) {
          case PAYERROR -> {
            recordInfo.setBillingstatus(BillingStatus.FAILED.name());
            recordInfo.setErrorinfo("订单支付失败，微信支付失败");
          }
          case SUCCESS -> {
            recordInfo.setBillingstatus(BillingStatus.SUCCESS.name());
          }
          case REFUND, REVOKED -> {
            recordInfo.setBillingstatus(BillingStatus.FAILED.name());
            recordInfo.setErrorinfo(
                String.format("微信支付失败,状态为:%s，请联系服务人员", wxPayStatus));
          }
          case NOTPAY -> {
            if (wxPayServiceMgr.closeBill(billingId, isHost)) {
              recordInfo.setBillingstatus(BillingStatus.CANCELED.name());
              recordInfo.setErrorinfo(NORMAL_CANCEL);
            } else {
              recordInfo.setBillingstatus(BillingStatus.FAILED.name());
              recordInfo.setErrorinfo(ABNORMAL_CANCEL);
            }
          }
          case USERPAYING -> {
            if (BillingStatusTask.MAX_TRY == this.getRetryCount()) {
              recordInfo.setBillingstatus(BillingStatus.FAILED.name());
              recordInfo.setErrorinfo("订单支付异常，银行处理超时，请联系服务人员");
            } else {
              return false;
            }
          }
          case CLOSED -> {
            recordInfo.setBillingstatus(BillingStatus.CANCELED.name());
          }
        }
        String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
        recordInfo.setCompletetime(time);
        recordInfo.setUpdatetime(time);
        billingRecordInfoRepository.update(recordInfo);
        return true;
      } catch (Exception e) {
        log.error("run wx pay status task fail due to db error!");
        return false;
      }
    }
    log.error("wx bill status task:{} failed, due to:{}", billingId, res.getErrorInfo());
    return false;
  }
}
