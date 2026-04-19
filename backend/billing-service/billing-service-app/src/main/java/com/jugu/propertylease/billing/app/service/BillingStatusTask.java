package com.jugu.propertylease.billing.app.service;

import lombok.Getter;

@Getter
public abstract class BillingStatusTask {

  public static final int MAX_TRY = 2;

  private int retryCount = 0;

  public boolean shouldRetry() {
    return this.retryCount < MAX_TRY;
  }

  public void addRetry() {
    retryCount++;
  }

  public abstract boolean run();
}
