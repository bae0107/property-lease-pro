package com.jugu.propertylease.device.app.thirdparty.yunding.handler;


import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingAccessTokenSglt;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.YunDingAccount;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @param <T> 三方服务统一的回复结构
 * @param <D> 云丁请求的外部response结果的extend
 */
public abstract class YunDingRequestHandler<T extends DeviceResponse, D extends YunDingError> {

  private static final String serviceName = "云丁";

  private static final String userAgent = "ddingnet-3.0";

  private final Supplier<YunDingAccount> yunDingAccountSupplier;

  public YunDingRequestHandler() {
    this.yunDingAccountSupplier = YunDingAccount::getInstance;
  }

  private Optional<String> genToken(YunDingAccount yunDingAccount) {
    return YunDingAccessTokenSglt.getToken(yunDingAccount);
  }

  public T submitRequest() {
    YunDingAccount yunDingAccount = yunDingAccountSupplier.get();
    Optional<String> tokenOp = genToken(yunDingAccount);
    if (tokenOp.isPresent()) {
      Sender<D> sender = buildSender();
      return handleRequest(tokenOp.get(), yunDingAccount.getDomain(), sender, serviceName);
    }
    return handleTokenError();
  }

  private Sender<D> buildSender() {
    return new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("User-Agent", userAgent);
      }
    });
  }

  public abstract T handleTokenError();

  public abstract T handleRequest(String token, String domain, Sender<D> sender,
      String serviceName);

  public abstract void parseSuccessResult(D yunDingResponse, T deviceResponse);

  public ErrorInfo genConnectionErrorInfo(String url) {
    ErrorTypesE typesE = ErrorTypesE.CONNECTION_ERROR;
    String msg = typesE.getErrorMsg();
    return new ErrorInfo(typesE.getErrorCode(), String.format(msg, serviceName, url));
  }
}
