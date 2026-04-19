package com.jugu.propertylease.billing.app.wxpay;

import com.jugu.propertylease.billing.app.service.PayNotifyTasks;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import jakarta.servlet.http.HttpServletRequest;

public interface WxPayServiceTasks {

  ReturnDataInfo<WxPayResponse.AppResponse> sendWxAppPay(WxPayDes wxPayDes, boolean isHost);

  ReturnDataInfo<WxPayResponse.QRResponse> sendWxQRPay(WxPayDes wxPayDes);

  ReturnDataInfo<WxPayResponse.WebResponse> sendWxH5WebPay(WxPayDes wxPayDes);

  ReturnDataInfo<WxPayResponse.WxPayStatus> checkPayStatus(String billingId, boolean isHost);

  String handWxNotify(HttpServletRequest request, PayNotifyTasks payNotifyTasks);

  boolean closeBill(String billingId, boolean isHost);
}
