package com.jugu.propertylease.billing.app.wxpay;

import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.utils.Common;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class WxPayProcessor<T, R> {

  private static final String REPEAT_MSG = "订单已支付,无需重复支付,单号:%s";

  private static final String ERROR_MSG = "支付请求失败,单号:%s,错误:%s";

  private static boolean isCommonParamValid(WxPayDes wxPayDes) {
    return !Common.isStringInValid(wxPayDes.getDes()) && !Common.isStringInValid(
        wxPayDes.getOrderId())
        && wxPayDes.getAmountCent() != 0;
  }

  public static String findWxPayValidTime(int mins) {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
    calendar.add(Calendar.MINUTE, mins);
    return formatter.format(calendar.getTime());
  }

  abstract boolean isPayRequestValid(WxPayDes wxPayDes);

  abstract void fillOrderV3Request(WxPayUnifiedOrderV3Request orderV3Request);

  abstract ReturnDataInfo<T> parseOrderV3Result(R orderV3Result);

  abstract R sendForResult(WxPayUnifiedOrderV3Request orderV3Request) throws WxPayException;

  public ReturnDataInfo<T> processWxPay(WxPayDes wxPayDes, WxPayConfig payConfig) {
    if (isCommonParamValid(wxPayDes) && isPayRequestValid(wxPayDes)) {
      String orderId = wxPayDes.getOrderId();
      WxPayUnifiedOrderV3Request orderV3Request = createV3Request(wxPayDes, orderId, payConfig);
      fillOrderV3Request(orderV3Request);
      try {
        R result = sendForResult(orderV3Request);
        return parseOrderV3Result(result);
      } catch (WxPayException e) {
        String code = e.getErrCode();
        if (!Common.isStringInValid(code)) {
          if (code.equals("ORDERPAID") || code.equals("ORDER_PAID")) {
            return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR,
                String.format(REPEAT_MSG, orderId));
          }
        }
        log.error("wx pay mistake for orderId:{}", orderId);
        log.error("wx pay mistake:" + e);
        return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR,
            String.format(ERROR_MSG, orderId, e));
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  private WxPayUnifiedOrderV3Request createV3Request(WxPayDes wxPayDes, String orderId,
      WxPayConfig payConfig) {
    WxPayUnifiedOrderV3Request orderV3Request = new WxPayUnifiedOrderV3Request();
    orderV3Request.setDescription(wxPayDes.getDes());
    orderV3Request.setOutTradeNo(orderId);
    orderV3Request.setNotifyUrl(payConfig.getNotifyUrl());
    WxPayUnifiedOrderV3Request.Amount amount = new WxPayUnifiedOrderV3Request.Amount();
    amount.setTotal(wxPayDes.getAmountCent());
    amount.setCurrency("CNY");
    orderV3Request.setAmount(amount);
    orderV3Request.setTimeExpire(wxPayDes.getExpireTime());
    return orderV3Request;
  }
}
