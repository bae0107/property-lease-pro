package com.jugu.propertylease.device.app.task;

public abstract class DeviceTask<U, T> {

  private final U responseData;

  public DeviceTask(U responseData) {
    this.responseData = responseData;
  }

  public abstract boolean isTaskSuccess(U responseData);

  public abstract T successCallback(U responseData);

  public abstract T failCallback(U responseData);

  public T runTask() {
    if (isTaskSuccess(responseData)) {
      return successCallback(responseData);
    } else {
      return failCallback(responseData);
    }
  }
}
