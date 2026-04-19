package com.jugu.propertylease.billing.app;

import com.jugu.propertylease.billing.app.service.BillingService;
import com.jugu.propertylease.billing.app.util.BillingIdTools;
import com.jugu.propertylease.billing.app.wxpay.WxPayDes;
import com.jugu.propertylease.billing.app.wxpay.WxPayProcessor;
import com.jugu.propertylease.billing.app.wxpay.WxPayProperty;
import com.jugu.propertylease.billing.app.wxpay.WxPayServiceMgr;
import com.jugu.propertylease.billing.common.entity.BillingRequest;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BillingServiceAppApplication.class)
public class BillingServiceAppTests {

  @Autowired
  private WxPayProperty wxPayProperty;

  @Autowired
  private WxPayServiceMgr wxPayServiceMgr;

  @Autowired
  private BillingService billingService;


  @Test
  void demoTest() {
    String[] params = "prepay_id=2121".split("prepay_id=");
    System.out.println(Arrays.toString(params));
  }

  @Test
  void wxPayPropTest() {
    System.out.println(wxPayProperty);
  }

  @Test
  void wxPayAppPayTest() {
    WxPayDes wxPayDes = new WxPayDes();
    wxPayDes.setDes("test");
    wxPayDes.setAmountCent(100);
    wxPayDes.setOpenId("ojDEB5hoHqhjHgyJF0jWN9j3Zv7w");
    wxPayDes.setOrderId("123456");
    wxPayDes.setOrderId("12345677");
    ReturnDataInfo<WxPayResponse.AppResponse> res = wxPayServiceMgr.sendWxAppPay(wxPayDes, true);
    System.out.println(res.isSuccess());
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void wxPayWebPayTest() {
    WxPayDes wxPayDes = new WxPayDes();
    wxPayDes.setDes("test");
    wxPayDes.setAmountCent(100);
    wxPayDes.setOrderId("12345677ds");
    wxPayDes.setIp("10.112.23.43");
    ReturnDataInfo<WxPayResponse.WebResponse> res = wxPayServiceMgr.sendWxH5WebPay(wxPayDes);
    System.out.println(res.isSuccess());
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void wxPayQRPayTest() {
    WxPayDes wxPayDes = new WxPayDes();
    wxPayDes.setDes("test");
    wxPayDes.setAmountCent(100);
    wxPayDes.setOrderId("12345677dsdd");
    wxPayDes.setIp("10.112.23.43");
    ReturnDataInfo<WxPayResponse.QRResponse> res = wxPayServiceMgr.sendWxQRPay(wxPayDes);
    System.out.println(res.isSuccess());
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void wxPayStatusTest() {
    WxPayDes wxPayDes = new WxPayDes();
    wxPayDes.setDes("test");
    wxPayDes.setAmountCent(100);
    wxPayDes.setOrderId("12345677dsdd");
    wxPayDes.setIp("10.112.23.43");
    ReturnDataInfo<WxPayResponse.WxPayStatus> res = wxPayServiceMgr.checkPayStatus("12345677dsdd",
        true);
    System.out.println(res.isSuccess());
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void BillingIdTest() {
    String a = BillingIdTools.generateBillingId("12", 1123213121);
    System.out.println(a);
  }

  @Test
  void creatBillTest() {
    BillingRequest billing = new BillingRequest();
    billing.setBillingType("account");
    billing.setAmountCent(100);
    billing.setUserId("101");
    RequestDataInfo<BillingRequest> billingRequest = new RequestDataInfo<>("id", "name", billing);
    ReturnDataInfo<String> res = billingService.createBill(billingRequest);

    System.out.println(res.isSuccess());
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void wxTimeTest() {
    System.out.println(WxPayProcessor.findWxPayValidTime(5));
  }
}
