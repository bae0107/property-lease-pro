package com.jugu.propertylease.device.app.thirdparty.heyi.handler;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiAccessTokenSglt;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.HeYiAccount;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class HeYiRequestHandler<T extends DeviceResponse, D> {

  protected static final String FORM = "application/x-www-form-urlencoded";

  protected static final String JSON = "application/json";

  private static final String serviceName = "合一";

  private static final int MAX_RETRY = 2;
  private final String contentType;
  private final Supplier<HeYiAccount> heYiAccountSupplier;
  private int retryNow = 0;

  public HeYiRequestHandler(String contentType) {
    this.heYiAccountSupplier = HeYiAccount::getInstance;
    this.contentType = contentType;
  }

  private Optional<String> genToken(HeYiAccount heYiAccount) {
    return HeYiAccessTokenSglt.getToken(heYiAccount);
  }

  public T submitRequest() {
    while (retryNow <= MAX_RETRY) {
      HeYiAccount heYiAccount = heYiAccountSupplier.get();
      Optional<String> tokenOp = genToken(heYiAccount);
      if (tokenOp.isEmpty()) {
        return handleTokenError();
      }
      Sender<HeYiResponse<D>> sender = buildSender(tokenOp.get(), contentType);
      try {
        return handleRequest(heYiAccount.getDomain(), sender, serviceName);
      } catch (TokenException e) {
        log.error(e.getMessage());
        if (this.retryNow < MAX_RETRY) {
          log.warn("start he yi request retry due to token expire");
          HeYiAccessTokenSglt.expireAndRefreshToken(heYiAccount);
        }
        retryNow++;
      }
    }
    log.error("he yi request failed after token retry!");
    return handleRetryFailedError();
  }

  private Sender<HeYiResponse<D>> buildSender(String token, String contentType) {
    return new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", contentType);
      }
    });
  }

  private ErrorInfo genConnectionErrorInfo(String url) {
    ErrorTypesE typesE = ErrorTypesE.CONNECTION_ERROR;
    String msg = typesE.getErrorMsg();
    return new ErrorInfo(typesE.getErrorCode(), String.format(msg, serviceName, url));
  }

  public T handleRetryFailedError() {
    ErrorType errorType = ErrorType.SERIOUS_ERROR;
    ErrorInfo errorInfo = new ErrorInfo(errorType.getErrorCode(),
        String.format(errorType.getErrorMsg(), "合一token过期重试无效"));
    return parseFailResult(errorInfo);
  }

  public T handleTokenError() {
    ErrorType errorType = ErrorType.SERIOUS_ERROR;
    ErrorInfo errorInfo = new ErrorInfo(errorType.getErrorCode(),
        String.format(errorType.getErrorMsg(), "合一token过期重试无效"));
    return parseFailResult(errorInfo);
  }

  public abstract T parseFailResult(ErrorInfo errorInfo);

  public abstract T handleRequest(String domain, Sender<HeYiResponse<D>> sender, String serviceName)
      throws TokenException;

  public abstract T parseSuccessResult(D responseObject);

  public T handleRequestError(IOException e, String url) throws TokenException {
    if (e != null) {
      String msg = e.getMessage();
      if (!Common.isStringInValid(msg) && msg.contains("invalid_token")) {
        log.error("he yi task:{} failed duo to token invalid!", url);
        throw new TokenException("invalid he yi token:" + msg);
      }
    }
    return parseFailResult(genConnectionErrorInfo(url));
  }
}
