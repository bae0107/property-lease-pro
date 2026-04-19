package com.jugu.propertylease.main.outer.wx;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class WxOpenServiceTools {

  private final WxMpService mpService;

  private final WxOpenConfig openConfig;

  public WxOpenServiceTools(WxOpenConfig config) {
    WxMpDefaultConfigImpl defaultConfig = new WxMpDefaultConfigImpl();
    defaultConfig.setAppId(config.getAppId());
    defaultConfig.setSecret(config.getAppSecret());
    WxMpService mpService = new WxMpServiceImpl();
    mpService.setWxMpConfigStorage(defaultConfig);
    this.mpService = mpService;
    this.openConfig = config;
  }

  /**
   * 微信登录二维码接口
   *
   * @param httpSession 当前session，保存防篡改参数，微信回调里会携带这个请求的参数，如果和传入的一致则通过
   * @return 定向打开二维码的URL
   */
  public String genLoginQR(HttpSession httpSession) {
    String state = UUID.randomUUID().toString().replace("-", "");
    httpSession.setAttribute("wx_login_state", state);
    return mpService.buildQrConnectUrl(openConfig.getNotifyUrl(), "snsapi_login", state);
  }

  /**
   * 获取用户openId(用户微信唯一身份参数）
   *
   * @param code    回调传入参数
   * @param state   防篡改参数
   * @param session 当前session
   * @return openId
   */
  public Optional<String> getUserOpenId(String code, String state, HttpSession session) {
    String savedState = (String) session.getAttribute("wx_login_state");
    if (state == null || !state.equals(savedState)) {
      log.error("state is illegal: send:{}, receive:{}", savedState, state);
      return Optional.empty();
    }
    session.removeAttribute("wx_login_state");
    try {
      WxOAuth2AccessToken token = mpService.getOAuth2Service().getAccessToken(code);
      WxOAuth2UserInfo user = mpService.getOAuth2Service().getUserInfo(token, "zh_CN");
      return Optional.of(user.getOpenid());
    } catch (WxErrorException e) {
      log.error("fail to parse wx login callback: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
