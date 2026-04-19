package com.jugu.propertylease.device.app.thirdparty.heyi.handler;

import java.io.Serial;

public class TokenException extends Exception {

  @Serial
  private static final long serialVersionUID = 1L;

  public TokenException(String message) {
    super(message);
  }
}
