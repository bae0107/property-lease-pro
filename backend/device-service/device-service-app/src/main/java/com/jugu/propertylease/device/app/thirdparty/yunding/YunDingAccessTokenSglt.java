package com.jugu.propertylease.device.app.thirdparty.yunding;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.YunDingAccount;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Calendar;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class YunDingAccessTokenSglt {

  private volatile static YunDingAccessTokenSglt yunDingAccessTokenSglt;

  private Token token;

  private YunDingAccessTokenSglt(Token token) {
    this.token = token;
  }

  public static Optional<String> getToken(YunDingAccount yunDingAccount) {
    if (yunDingAccessTokenSglt == null) {
      synchronized (YunDingAccessTokenSglt.class) {
        if (yunDingAccessTokenSglt == null) {
          Optional<Token> tokenOp = refreshToken(yunDingAccount);
          if (tokenOp.isPresent()) {
            yunDingAccessTokenSglt = new YunDingAccessTokenSglt(tokenOp.get());
            return Optional.of(yunDingAccessTokenSglt.token.accessToken);
          }
          return Optional.empty();
        }
      }
    }
    return yunDingAccessTokenSglt.checkToken(yunDingAccount);
  }

  public static Optional<String> forceUpdateToken(YunDingAccount yunDingAccount) {
    synchronized (YunDingAccessTokenSglt.class) {
      if (yunDingAccessTokenSglt == null) {
        return getToken(yunDingAccount);
      }
      Token token = yunDingAccessTokenSglt.token;
      if (token != null) {
        yunDingAccessTokenSglt.token = null;
      }
      return getToken(yunDingAccount);
    }
  }

  private static Optional<Token> refreshToken(YunDingAccount yunDingAccount) {
    String id = yunDingAccount.getClientId();
    String sec = yunDingAccount.getClientSecret();
    if (Common.isStringInValid(id) || Common.isStringInValid(sec)) {
      return Optional.empty();
    }
    Sender<Token> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("User-Agent", "ddingnet-3.0");
      }
    });

    String url = yunDingAccount.getDomain() + YunDingUrlE.ACCESS_TOKEN.getUrl();
    Optional<Token> tokenOptional = sender.sendPostRequest(url,
        GsonFactory.toJson(yunDingAccount.toDTO()), Token.class);
    if (tokenOptional.isPresent()) {
      Token token = tokenOptional.get();
      int errorCode = token.getErrCode();
      String errorMsg = token.getErrMsg();
      String tokenStr = token.accessToken;
      if (errorCode > 0 || Common.isStringInValid(tokenStr)) {
        log.error("yun ding access token failed due to error:{}, msg:{}", errorCode, errorMsg);
        return Optional.empty();
      }
      if (token.calExpireTime()) {
        return Optional.of(token);
      }
    }
    return Optional.empty();
  }

  private Optional<String> checkToken(YunDingAccount yunDingAccount) {
    String currentTime = Common.findSysTime();
    Token token = yunDingAccessTokenSglt.token;
    synchronized (YunDingAccessTokenSglt.class) {
      if (token == null || currentTime.compareTo(token.expireTime) > 0) {
        Optional<Token> tokenOp = refreshToken(yunDingAccount);
        if (tokenOp.isPresent()) {
          yunDingAccessTokenSglt.token = tokenOp.get();
          return Optional.of(yunDingAccessTokenSglt.token.accessToken);
        }
        return Optional.empty();
      }
      return Optional.of(token.accessToken);
    }
  }

  @Getter
  @Setter
  @ToString
  private static class Token extends YunDingError {

    private static final int BEFORE_DAY = -2;

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("expires_time")
    private int expiresIn;

    private String expireTime;

    public Token(String accessToken, int expiresIn, int errCode, String errMsg) {
      super(errCode, errMsg);
      this.accessToken = accessToken;
      this.expiresIn = expiresIn;
    }

    private boolean calExpireTime() {
      if (expiresIn > 0) {
        String time = Common.findTimeBySecondTimestamp(expiresIn);
        Optional<String> expTime = Common.findSysTimeAfter(time, BEFORE_DAY, Calendar.DATE);
        if (expTime.isPresent()) {
          this.expireTime = expTime.get();
          return true;
        }
      }
      return false;
    }
  }
}
