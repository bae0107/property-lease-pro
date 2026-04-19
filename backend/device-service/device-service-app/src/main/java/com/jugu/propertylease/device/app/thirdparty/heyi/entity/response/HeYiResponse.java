package com.jugu.propertylease.device.app.thirdparty.heyi.entity.response;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class HeYiResponse<T> {

  @SerializedName("status")
  private int status;

  @SerializedName("message")
  private String message;

  @SerializedName("object")
  private T object;

  public HeYiResponse(int status, String message, T object) {
    this.status = status;
    this.message = message;
    this.object = object;
  }
}
