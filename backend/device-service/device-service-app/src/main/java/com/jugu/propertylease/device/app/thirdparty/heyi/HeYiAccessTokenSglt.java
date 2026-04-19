package com.jugu.propertylease.device.app.thirdparty.heyi;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.HeYiAccount;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@SuppressWarnings("ClassCanBeRecord")
@Log4j2
public class HeYiAccessTokenSglt {

  private volatile static HeYiAccessTokenSglt heYiAccessTokenSglt;

  private final Token token;

  private HeYiAccessTokenSglt(HeYiAccessTokenSglt.Token token) {
    this.token = token;
  }

  public static Optional<String> getToken(HeYiAccount heYiAccount) {
    if (heYiAccessTokenSglt == null) {
      synchronized (HeYiAccessTokenSglt.class) {
        if (heYiAccessTokenSglt == null) {
          Optional<Token> tokenOp = sendTokenRequest(heYiAccount);
          if (tokenOp.isPresent()) {
            heYiAccessTokenSglt = new HeYiAccessTokenSglt(tokenOp.get());
            return Optional.of(heYiAccessTokenSglt.token.accessToken);
          }
          return Optional.empty();
        }
      }
    }
    return Optional.of(heYiAccessTokenSglt.token.accessToken);
  }

  public static void expireAndRefreshToken(HeYiAccount heYiAccount) {
    log.warn("expire and refresh he yi token");
    synchronized (HeYiAccessTokenSglt.class) {
      Optional<Token> tokenOp = sendTokenRequest(heYiAccount);
      if (tokenOp.isPresent()) {
        heYiAccessTokenSglt = new HeYiAccessTokenSglt(tokenOp.get());
      } else {
        log.error("expire and refresh he yi token failed");
        heYiAccessTokenSglt = null;
      }
    }
  }

  private static Optional<Token> sendTokenRequest(HeYiAccount heYiAccount) {
    String id = heYiAccount.getClientId();
    String sec = heYiAccount.getClientSecret();
    if (Common.isStringInValid(id) || Common.isStringInValid(sec)) {
      return Optional.empty();
    }
    Sender<Token> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      }
    });
    String url = heYiAccount.getDomain() + HeYiUrlE.ACCESS_TOKEN.getUrl();
    Optional<Token> tokenOptional = sender.sendPostRequest(url, heYiAccount.toDTO().toForm(),
        HeYiAccessTokenSglt.Token.class);
    if (tokenOptional.isPresent()) {
      HeYiAccessTokenSglt.Token tokenRes = tokenOptional.get();
      String token = tokenRes.accessToken;
      if (Common.isStringInValid(token)) {
        log.error("fail to get he yi access token, error:{}, code:{}, msg:{}", tokenRes.error,
            tokenRes.errorCode, tokenRes.errorDescription);
        return Optional.empty();
      }
      return Optional.of(tokenRes);
    }
    log.error("fail to get he yi access token due to io exception");
    return Optional.empty();
  }

  @Getter
  @Setter
  @ToString
  @NoArgsConstructor
  private static class Token {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("expires_in")
    private int expiresIn;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("error_code")
    private String errorCode;

    @SerializedName("error")
    private String error;

    @SerializedName("error_description")
    private String errorDescription;
  }
}
