package com.jugu.propertylease.billing.app.wxpay;

import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyV3Result;
import com.github.binarywang.wxpay.bean.request.WxPayOrderCloseV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.jugu.propertylease.billing.app.service.PayNotifyTasks;
import com.jugu.propertylease.billing.common.entity.wx.WxPayRequest;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.billing.common.entity.wx.WxPayType;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.utils.Common;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@SuppressWarnings("ClassCanBeRecord")
@Component
@Log4j2
public class WxPayServiceMgr implements WxPayServiceTasks {

  private final WxPayProperty wxPayProperty;

  public WxPayServiceMgr(WxPayProperty wxPayProperty) {
    this.wxPayProperty = wxPayProperty;
  }

  private static Optional<String> fetchRequestToString(HttpServletRequest request) {
    try (BufferedReader bufferedReader = new BufferedReader(
        new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder stringBuilder = new StringBuilder();
      String input;
      while ((input = bufferedReader.readLine()) != null) {
        stringBuilder.append(input);
      }
      log.info("wx pay response:{}", stringBuilder);
      return Optional.of(stringBuilder.toString());

    } catch (IOException e) {
      log.error("wx pay io error: " + e);
      return Optional.empty();
    }
  }

  private WxPayService createService(String appId) {
    WxPayService wxPayService = new WxPayServiceImpl();
    wxPayService.setConfig(createConfig(appId));
    return wxPayService;
  }

  private WxPayConfig createConfig(String appId) {
    WxPayConfig payConfig = new WxPayConfig();
    payConfig.setMchId(StringUtils.trimToNull(wxPayProperty.getMchId()));
    payConfig.setApiV3Key(StringUtils.trimToNull(wxPayProperty.getApiV3Key()));
    payConfig.setPrivateCertPath(StringUtils.trimToNull(wxPayProperty.getPrivateCert()));
    payConfig.setPrivateKeyPath(StringUtils.trimToNull(wxPayProperty.getPrivateKey()));
    payConfig.setNotifyUrl(StringUtils.trimToNull(wxPayProperty.getNotifyUrl()));
    payConfig.setUseSandboxEnv(false);
    payConfig.setAppId(StringUtils.trimToNull(appId));
    return payConfig;
  }

  @Override
  public ReturnDataInfo<WxPayResponse.AppResponse> sendWxAppPay(WxPayDes wxPayDes, boolean isHost) {
    String appId = isHost ? wxPayProperty.getHostAppId() : wxPayProperty.getUserAppId();
    WxPayService wxPayService = createService(appId);
    WxPayProcessor<WxPayResponse.AppResponse, WxPayUnifiedOrderV3Result.JsapiResult> processor = new WxPayProcessor<>() {
      @Override
      boolean isPayRequestValid(WxPayDes payDes) {
        return !Common.isStringInValid(payDes.getOpenId());
      }

      @Override
      void fillOrderV3Request(WxPayUnifiedOrderV3Request orderV3Request) {
        WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
        payer.setOpenid(wxPayDes.getOpenId());
        orderV3Request.setPayer(payer);
      }

      @Override
      ReturnDataInfo<WxPayResponse.AppResponse> parseOrderV3Result(
          WxPayUnifiedOrderV3Result.JsapiResult orderV3Result) {
        WxPayResponse.AppResponse appResponse = new WxPayResponse.AppResponse();
        appResponse.setAppId(appId);
        appResponse.setTimeStamp(orderV3Result.getTimeStamp());
        appResponse.setRandomStr(orderV3Result.getNonceStr());
        String packageValue = orderV3Result.getPackageValue();
        String[] params = packageValue.split("prepay_id=");
        if (params.length < 2) {
          log.error("invalid wx prepay id for order: {}", wxPayDes.getOrderId());
          return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, "微信prepay id异常");
        }
        appResponse.setPrepayId(params[1]);
        appResponse.setSignType(orderV3Result.getSignType());
        appResponse.setPaySign(orderV3Result.getPaySign());
        appResponse.setExpireTime(wxPayDes.getExpireTime());
        return ReturnDataInfo.successData(appResponse);
      }

      @Override
      WxPayUnifiedOrderV3Result.JsapiResult sendForResult(WxPayUnifiedOrderV3Request orderV3Request)
          throws WxPayException {
        return wxPayService.createOrderV3(TradeTypeEnum.JSAPI, orderV3Request);
      }
    };
    return processor.processWxPay(wxPayDes, wxPayService.getConfig());
  }

  @Override
  public ReturnDataInfo<WxPayResponse.QRResponse> sendWxQRPay(WxPayDes wxPayDes) {
    WxPayService wxPayService = createService(wxPayProperty.getHostAppId());
    WxPayProcessor<WxPayResponse.QRResponse, String> processor = new WxPayProcessor<>() {
      @Override
      boolean isPayRequestValid(WxPayDes payDes) {
        return !Common.isStringInValid(payDes.getIp());
      }

      @Override
      void fillOrderV3Request(WxPayUnifiedOrderV3Request orderV3Request) {
        WxPayUnifiedOrderV3Request.SceneInfo sceneInfo = new WxPayUnifiedOrderV3Request.SceneInfo();
        sceneInfo.setPayerClientIp(wxPayDes.getIp());
        orderV3Request.setSceneInfo(sceneInfo);
      }

      @Override
      ReturnDataInfo<WxPayResponse.QRResponse> parseOrderV3Result(String orderV3Result) {
        if (Common.isStringInValid(orderV3Result)) {
          return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, "二维码链接无效");
        }
        WxPayResponse.QRResponse qrResponse = new WxPayResponse.QRResponse();
        qrResponse.setQrStr(orderV3Result);
        qrResponse.setExpireTime(wxPayDes.getExpireTime());
        return ReturnDataInfo.successData(qrResponse);
      }

      @Override
      String sendForResult(WxPayUnifiedOrderV3Request orderV3Request) throws WxPayException {
        return wxPayService.createOrderV3(TradeTypeEnum.NATIVE, orderV3Request);
      }
    };
    return processor.processWxPay(wxPayDes, wxPayService.getConfig());
  }

  @Override
  public ReturnDataInfo<WxPayResponse.WebResponse> sendWxH5WebPay(WxPayDes wxPayDes) {
    WxPayService wxPayService = createService(wxPayProperty.getHostAppId());
    WxPayProcessor<WxPayResponse.WebResponse, String> processor = new WxPayProcessor<>() {
      @Override
      boolean isPayRequestValid(WxPayDes payDes) {
        return !Common.isStringInValid(payDes.getIp());
      }

      @Override
      void fillOrderV3Request(WxPayUnifiedOrderV3Request orderV3Request) {
        WxPayUnifiedOrderV3Request.SceneInfo sceneInfo = new WxPayUnifiedOrderV3Request.SceneInfo();
        WxPayUnifiedOrderV3Request.H5Info h5Info = new WxPayUnifiedOrderV3Request.H5Info();
        h5Info.setType("MWEB");
        sceneInfo.setPayerClientIp(wxPayDes.getIp());
        sceneInfo.setH5Info(h5Info);
        orderV3Request.setSceneInfo(sceneInfo);
      }

      @Override
      ReturnDataInfo<WxPayResponse.WebResponse> parseOrderV3Result(String orderV3Result) {
        if (Common.isStringInValid(orderV3Result)) {
          return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, "支付链接无效");
        }
        WxPayResponse.WebResponse webResponse = new WxPayResponse.WebResponse();
        webResponse.setPayUrl(orderV3Result);
        webResponse.setExpireTime(wxPayDes.getExpireTime());
        return ReturnDataInfo.successData(webResponse);
      }

      @Override
      String sendForResult(WxPayUnifiedOrderV3Request orderV3Request) throws WxPayException {
        return wxPayService.createOrderV3(TradeTypeEnum.H5, orderV3Request);
      }
    };
    return processor.processWxPay(wxPayDes, wxPayService.getConfig());
  }

  @Override
  public ReturnDataInfo<WxPayResponse.WxPayStatus> checkPayStatus(String billing, boolean isHost) {
    String appId = isHost ? wxPayProperty.getHostAppId() : wxPayProperty.getUserAppId();
    WxPayService wxPayService = createService(appId);
    try {
      WxPayOrderQueryV3Result queryV3Result = wxPayService.queryOrderV3(null, billing);
      String tradeState = queryV3Result.getTradeState();
      if (Common.isStringInValid(tradeState)) {
        return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, "第三方支付状态无效");
      }
      WxPayResponse.WxPayStatus status = WxPayResponse.WxPayStatus.findStatusByResult(tradeState);
      if (status == WxPayResponse.WxPayStatus.UNKNOWN) {
        log.error("wx check order mistake for orderId:{}, due to unknown status:{}", billing,
            status);
        return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, "第三方支付状态无效");
      }
      return ReturnDataInfo.successData(status);
    } catch (WxPayException e) {
      log.error("wx check order mistake for orderId:{}", billing);
      log.error("wx check order mistake:" + e);
      return ReturnDataInfo.failDataByType(ErrorType.OUT_ERROR, String.valueOf(e));
    }
  }

  @Override
  public String handWxNotify(HttpServletRequest request, PayNotifyTasks payNotifyTasks) {
    WxPayService wxPayService = createService(wxPayProperty.getHostAppId());
    Optional<String> resultStr = fetchRequestToString(request);
    if (resultStr.isEmpty()) {
      return WxPayNotifyResult.error("io error");
    }
    SignatureHeader signatureHeader = genSignHeader(request);
    try {
      WxPayOrderNotifyV3Result notifyResult = wxPayService.parseOrderNotifyV3Result(resultStr.get(),
          signatureHeader);
      WxPayOrderNotifyV3Result.DecryptNotifyResult result = notifyResult.getResult();
      if (WxPayConstants.WxpayTradeStatus.SUCCESS.equals(result.getTradeState())) {
        String billingId = result.getOutTradeNo();
        if (Common.isStringInValid(billingId)) {
          return WxPayNotifyResult.error("out trade empty");
        }
        try {
          payNotifyTasks.success(billingId);
          return WxPayNotifyResult.success();
        } catch (Exception e) {
          return WxPayNotifyResult.error("system error");
        }
      }
      return WxPayNotifyResult.error("trade state invalid");
    } catch (WxPayException e) {
      return WxPayNotifyResult.error(e.getMessage());
    }
  }

  @Override
  public boolean closeBill(String billingId, boolean isHost) {
    String appId = isHost ? wxPayProperty.getHostAppId() : wxPayProperty.getUserAppId();
    WxPayService wxPayService = createService(appId);
    WxPayOrderCloseV3Request closeV3Request = new WxPayOrderCloseV3Request();
    closeV3Request.setMchid(wxPayService.getConfig().getMchId());
    closeV3Request.setOutTradeNo(billingId);
    try {
      wxPayService.closeOrderV3(closeV3Request);
      return true;
    } catch (WxPayException e) {
      log.error("fail to close order: {}", billingId);
      log.error("fail to close order mistake" + e);
      return false;
    }
  }

  public ReturnDataInfo<WxPayResponse> payWxByPayType(WxPayType payType, WxPayDes payDes,
      WxPayRequest wxPayRequest, boolean isHost) {
    WxPayResponse wxPayResponse = new WxPayResponse();
    switch (payType) {
      case APP -> {
        payDes.setOpenId(wxPayRequest.getOpenId());
        ReturnDataInfo<WxPayResponse.AppResponse> response = sendWxAppPay(payDes, isHost);
        if (response.isSuccess()) {
          wxPayResponse.setAppResponse(response.getResponseData());
          return ReturnDataInfo.successData(wxPayResponse);
        }
        return ReturnDataInfo.failData(response.getErrorInfo());
      }
      case QR -> {
        payDes.setIp(wxPayRequest.getIp());
        ReturnDataInfo<WxPayResponse.QRResponse> response = sendWxQRPay(payDes);
        if (response.isSuccess()) {
          wxPayResponse.setQrResponse(response.getResponseData());
          return ReturnDataInfo.successData(wxPayResponse);
        }
        return ReturnDataInfo.failData(response.getErrorInfo());
      }
      case H5 -> {
        payDes.setIp(wxPayRequest.getIp());
        ReturnDataInfo<WxPayResponse.WebResponse> response = sendWxH5WebPay(payDes);
        if (response.isSuccess()) {
          wxPayResponse.setWebResponse(response.getResponseData());
          return ReturnDataInfo.successData(wxPayResponse);
        }
        return ReturnDataInfo.failData(response.getErrorInfo());
      }
      default -> {
        log.error("send wx pay failed due to unknown wx pay type: {}", payType);
        return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
      }
    }
  }

  private SignatureHeader genSignHeader(HttpServletRequest request) {
    SignatureHeader signatureHeader = new SignatureHeader();
    signatureHeader.setSignature(request.getHeader("Wechatpay-Signature"));
    signatureHeader.setNonce(request.getHeader("Wechatpay-Nonce"));
    signatureHeader.setSerial(request.getHeader("Wechatpay-Serial"));
    signatureHeader.setTimeStamp(request.getHeader("Wechatpay-TimeStamp"));
    return signatureHeader;
  }
}
