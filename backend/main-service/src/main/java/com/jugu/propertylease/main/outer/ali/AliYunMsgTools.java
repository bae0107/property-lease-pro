package com.jugu.propertylease.main.outer.ali;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendBatchSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendBatchSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendBatchSmsResponseBody;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class AliYunMsgTools {

  private final AliYunConfig aliYunConfig;

  private final Client client;

  public AliYunMsgTools(AliYunConfig aliYunConfig) {
    try {
      Config config = new Config();
      config.setAccessKeyId(aliYunConfig.getId());
      config.setAccessKeySecret(aliYunConfig.getKey());
      config.endpoint = aliYunConfig.getUrl();
      config.regionId = aliYunConfig.getRegion();
      this.client = new Client(config);
      this.aliYunConfig = aliYunConfig;
    } catch (Exception e) {
      throw new RuntimeException("ali yun msg construct failed:{}" + e.getMessage(), e);
    }
  }

  /**
   * 短信单条发送接口
   *
   * @param msgOp 详见AliYunMsgOp，不同的模板有不同的结构
   * @param phone 接收手机号（不需要+86）
   * @param cts   信息可变参数，详见msgOp中的format结构
   * @return 是否发送成功
   */
  public boolean sendDomesticMsg(AliYunMsgOp msgOp, String phone, String... cts) {
    String msg = msgOp.buildCt(cts);
    try {
      SendSmsRequest sendSmsRequest = new SendSmsRequest()
          .setPhoneNumbers(phone)
          .setSignName(aliYunConfig.getSign())
          .setTemplateCode(msgOp.getTempId())
          .setTemplateParam(msg);
      SendSmsResponse response = client.sendSms(sendSmsRequest);
      SendSmsResponseBody body = response.getBody();
      return isMsgSuccess(body.getCode(), body.getMessage(), phone);
    } catch (Exception e) {
      log.error("fail to send ali yun msg to:{}, ct:{}, error:{}!", phone, msg, e.getMessage());
      return false;
    }
  }

  /**
   * 短信群发接口
   *
   * @param msgOp  详见AliYunMsgOp，不同的模板有不同的结构
   * @param phones 接收手机号集群（不需要+86）
   * @param cts    信息可变参数，详见msgOp中的format结构
   * @return 是否发送成功
   */
  public boolean sendDomesticMsgs(AliYunMsgOp msgOp, List<String> phones, String... cts) {
    String msg = msgOp.buildCt(cts);
    try {
      List<String> signName = new ArrayList<>();
      List<String> ctList = new ArrayList<>();
      String sign = aliYunConfig.getSign();
      for (int i = 0; i < phones.size(); i++) {
        signName.add(sign);
        ctList.add(msg);
      }
      String phonesStr = GsonFactory.toJson(phones);
      SendBatchSmsRequest batchSmsRequest = new SendBatchSmsRequest()
          .setPhoneNumberJson(phonesStr)
          .setSignNameJson(GsonFactory.toJson(signName))
          .setTemplateCode(msgOp.getTempId())
          .setTemplateParamJson(GsonFactory.toJson(ctList));
      SendBatchSmsResponse response = client.sendBatchSms(batchSmsRequest);
      SendBatchSmsResponseBody body = response.getBody();
      return isMsgSuccess(body.getCode(), body.getMessage(), phonesStr);
    } catch (Exception e) {
      log.error("fail to send ali yun batch msg to:{}, ct:{}, error:{}!", phones, msg,
          e.getMessage());
      return false;
    }
  }

  private boolean isMsgSuccess(String code, String msg, String phones) {
    if (Common.isStringInValid(code)) {
      log.error("ali yun send msg failed to: {}, response code invalid", phones);
      return false;
    }
    if ("OK".equals(code.toUpperCase(Locale.ROOT).trim())) {
      return true;
    } else {
      log.error("ali yun send msg failed to: {}, code:{}, reason:{}", phones, code, msg);
      return false;
    }
  }

  public interface Builder {

    String buildCt(String... params) throws IllegalArgumentException;
  }
}
